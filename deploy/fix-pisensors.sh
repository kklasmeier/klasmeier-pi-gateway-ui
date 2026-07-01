#!/usr/bin/env bash
# Fix PiSensors after gateway UI install (run on PiSensors from repo root).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Kill stale process on :8000"
sudo fuser -k 8000/tcp 2>/dev/null || true
sleep 1

echo "==> Fix ui.env permissions (systemd injects env; app does not read file)"
if [[ -f /etc/pivpngateway-ui/ui.env ]]; then
  sudo chmod 640 /etc/pivpngateway-ui/ui.env
  sudo chown root:pi /etc/pivpngateway-ui/ui.env
fi

echo "==> Sync gateway UI app"
sudo rsync -a "${REPO_ROOT}/app/" "${REPO_ROOT}/static/" /opt/pivpngateway-ui/

echo "==> nginx"
sudo nginx -t
sudo systemctl reload nginx

echo "==> Restart services"
sudo systemctl restart pivpngateway-ui
sudo systemctl restart security-camera-central

sleep 2
echo "==> Listeners"
ss -tlnp | grep -E ':8000|:8001|:8002' || true
echo "==> Service status"
systemctl is-active pivpngateway-ui security-camera-central nginx
curl -s -o /dev/null -w "gateway health: %{http_code}\n" http://127.0.0.1:8001/health || true
