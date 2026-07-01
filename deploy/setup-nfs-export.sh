#!/usr/bin/env bash
# One-time: export git clone to Pi5Desktop for NFS editing (same pattern as Security-Camera-Central).
# Run on PiSensors: sudo ./deploy/setup-nfs-export.sh
set -euo pipefail

EXPORT_LINE='/home/pi/klasmeier-pi-gateway-ui 192.168.1.42(rw,sync,no_subtree_check)'

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run with sudo: sudo $0"
  exit 1
fi

apt-get install -y nfs-kernel-server >/dev/null 2>&1 || true

if ! grep -qF '/home/pi/klasmeier-pi-gateway-ui' /etc/exports; then
  echo "$EXPORT_LINE" >> /etc/exports
  echo "Added to /etc/exports"
else
  echo "Export already in /etc/exports"
fi

exportfs -ra
systemctl enable nfs-kernel-server >/dev/null 2>&1 || true
systemctl restart nfs-kernel-server

echo "Active exports:"
exportfs -v | grep klasmeier-pi-gateway-ui || exportfs -v
echo ""
echo "On Pi5Desktop: sudo mount /home/pi/klasmeier-pi-gateway-ui"
