#!/usr/bin/env bash
# Deploy UI from git clone to /opt/pivpngateway-ui (run on PiSensors after pull).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_ROOT="/opt/pivpngateway-ui"

echo "==> Deploy gateway UI to ${INSTALL_ROOT}"
sudo rsync -a --delete \
  --exclude venv \
  --exclude .env \
  "${REPO_ROOT}/app" \
  "${REPO_ROOT}/static" \
  "${REPO_ROOT}/requirements.txt" \
  "${INSTALL_ROOT}/"
sudo chown -R pi:pi "${INSTALL_ROOT}"

if [[ ! -d "${INSTALL_ROOT}/venv" ]]; then
  python3 -m venv "${INSTALL_ROOT}/venv"
fi
"${INSTALL_ROOT}/venv/bin/pip" install -q -U pip
"${INSTALL_ROOT}/venv/bin/pip" install -q -r "${INSTALL_ROOT}/requirements.txt"

sudo systemctl restart pivpngateway-ui
echo "==> Done. UI: http://$(hostname -I | awk '{print $1}'):8000/gateway/"
