#!/usr/bin/env bash
# Deploy gateway UI to PiSensors (.26). Run from repo root on the PiSensors host.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_ROOT="/opt/pivpngateway-ui"
ENV_DIR="/etc/pivpngateway-ui"
ENV_FILE="${ENV_DIR}/ui.env"
SCC_ENV="/home/pi/Security-Camera-Central/.env"

echo "==> Installing gateway UI to ${INSTALL_ROOT}"
sudo mkdir -p "${INSTALL_ROOT}" "${ENV_DIR}"
sudo rsync -a --delete \
  --exclude venv \
  --exclude .env \
  "${REPO_ROOT}/app" \
  "${REPO_ROOT}/static" \
  "${REPO_ROOT}/requirements.txt" \
  "${INSTALL_ROOT}/"
sudo chown -R pi:pi "${INSTALL_ROOT}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "==> Creating ${ENV_FILE} from example"
  if [[ -f "${SCC_ENV}" ]] && grep -q GATEWAY_API_URL "${SCC_ENV}" 2>/dev/null; then
    sudo tee "${ENV_FILE}" >/dev/null <<EOF
$(grep -E '^GATEWAY_' "${SCC_ENV}" || true)
UI_BIND=127.0.0.1:8001
EOF
  else
    sudo cp "${REPO_ROOT}/deploy/ui.env.example" "${ENV_FILE}"
  fi
  sudo chmod 640 "${ENV_FILE}"
  sudo chown root:pi "${ENV_FILE}"
  echo "    Edit ${ENV_FILE} if needed"
fi

echo "==> Python venv"
python3 -m venv "${INSTALL_ROOT}/venv"
"${INSTALL_ROOT}/venv/bin/pip" install -q -U pip
"${INSTALL_ROOT}/venv/bin/pip" install -q -r "${INSTALL_ROOT}/requirements.txt"

echo "==> systemd"
sudo cp "${REPO_ROOT}/deploy/pivpngateway-ui.service" /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable pivpngateway-ui.service
sudo systemctl restart pivpngateway-ui.service

echo "==> nginx (port 8000 front door)"
if grep -q '^API_HOST=' "${SCC_ENV}" 2>/dev/null; then
  sudo sed -i 's/^API_HOST=.*/API_HOST=127.0.0.1/' "${SCC_ENV}" || true
else
  echo 'API_HOST=127.0.0.1' | sudo tee -a "${SCC_ENV}" >/dev/null
fi
if grep -q '^API_PORT=' "${SCC_ENV}" 2>/dev/null; then
  sudo sed -i 's/^API_PORT=.*/API_PORT=8002/' "${SCC_ENV}" || true
else
  echo 'API_PORT=8002' | sudo tee -a "${SCC_ENV}" >/dev/null
fi

echo "==> Stopping camera API and clearing stale :8000 listeners"
sudo systemctl stop security-camera-central.service 2>/dev/null || true
sudo fuser -k 8000/tcp 2>/dev/null || true
sleep 1

sudo cp "${REPO_ROOT}/deploy/nginx-pisensors.conf" /etc/nginx/sites-available/pivpngateway
sudo ln -sf /etc/nginx/sites-available/pivpngateway /etc/nginx/sites-enabled/pivpngateway
sudo nginx -t
sudo systemctl reload nginx

echo "==> Restarting camera API on 127.0.0.1:8002"
sudo systemctl start security-camera-central.service

echo "==> Done. Gateway UI: http://$(hostname -I | awk '{print $1}'):8000/gateway/"
echo "    Ensure Security-Camera-Central uses API_HOST=127.0.0.1 API_PORT=8002"
