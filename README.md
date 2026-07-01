# Klasmeier Pi Gateway UI

Standalone management UI for the home VPN gateway. Runs on **PiSensors** (192.168.1.26); proxies to the gateway API on **piGateway** (192.168.1.100:8080).

**Browser URL:** http://192.168.1.26:8000/gateway/

## Architecture

| Component | Host | Port |
|-----------|------|------|
| nginx (public) | PiSensors | **8000** |
| This UI | PiSensors | 127.0.0.1:8001 |
| Security Camera API | PiSensors | 127.0.0.1:8002 |
| Gateway API | piGateway | 192.168.1.100:8080 |

## Deploy

See **[DEPLOY.md](DEPLOY.md)** for git workflow, first-time install, secrets, and troubleshooting.

Quick update on PiSensors:

```bash
cd ~/klasmeier-pi-gateway-ui && git pull
sudo rsync -a app/ static/ /opt/pivpngateway-ui/
sudo systemctl restart pivpngateway-ui
```

## Configuration

`/etc/pivpngateway-ui/ui.env` from `deploy/ui.env.example`:

- `GATEWAY_API_TOKEN` — must match [klasmeier-pi-gateway](https://github.com/kklasmeier/klasmeier-pi-gateway) `/etc/pivpngateway/api.env`
- `GATEWAY_UI_USER` / `GATEWAY_UI_PASSWORD` — Basic auth for the web UI

## Related repo

**klasmeier-pi-gateway** — API, firewall, DHCP, WireGuard on piGateway (.100).
