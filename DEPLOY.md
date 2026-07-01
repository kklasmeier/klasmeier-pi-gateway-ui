# Deploying klasmeier-pi-gateway-ui

Standalone gateway admin UI on **PiSensors** (`192.168.1.26`). Git work on **Pi5Desktop**; deploy on **PiSensors**.

**Related:** [klasmeier-pi-gateway](https://github.com/kklasmeier/klasmeier-pi-gateway) — API on piGateway (`.100`).

Browser URL: **http://192.168.1.26:8000/gateway/**

---

## Architecture (Option A)

```text
Browser :8000 (nginx on PiSensors)
    ├─ /gateway/*  →  pivpngateway-ui  (127.0.0.1:8001)  →  piGateway API :8080
    └─ /*          →  security-camera-central  (127.0.0.1:8002)
```

This repo is **not** part of Security-Camera-Central. The camera stack only needs `API_HOST=127.0.0.1` and `API_PORT=8002` in its `.env` so nginx can own port 8000.

---

## Git workflow (day to day)

### On Pi5Desktop (check-in)

```bash
cd ~/Programming/klasmeier-pi-gateway-ui
git status
git add …
git commit -m "Describe change"
git push
```

### On PiSensors (check-out + deploy)

```bash
cd ~/klasmeier-pi-gateway-ui
git pull
sudo rsync -a app/ static/ requirements.txt /opt/pivpngateway-ui/
sudo /opt/pivpngateway-ui/venv/bin/pip install -r /opt/pivpngateway-ui/requirements.txt
sudo systemctl restart pivpngateway-ui
```

Or run `deploy/install-pisensors.sh` for a full sync (includes venv rebuild).

**Do not overwrite** `/etc/pivpngateway-ui/ui.env` on pull.

---

## First-time install on PiSensors

### Prerequisites

- **nginx** installed and running
- **Security-Camera-Central** at `/home/pi/Security-Camera-Central` with working venv
- **Gateway API** reachable at `http://192.168.1.100:8080`
- Same **`GATEWAY_API_TOKEN`** on piGateway (`/etc/pivpngateway/api.env`) and here

### 1. Clone

```bash
git clone git@github.com:kklasmeier/klasmeier-pi-gateway-ui.git ~/klasmeier-pi-gateway-ui
cd ~/klasmeier-pi-gateway-ui
chmod +x deploy/*.sh
```

### 2. Secrets

| Template | Live path |
|----------|-----------|
| `deploy/ui.env.example` | `/etc/pivpngateway-ui/ui.env` |

```bash
sudo mkdir -p /etc/pivpngateway-ui
sudo cp deploy/ui.env.example /etc/pivpngateway-ui/ui.env
sudo chmod 640 /etc/pivpngateway-ui/ui.env
sudo chown root:pi /etc/pivpngateway-ui/ui.env
sudo nano /etc/pivpngateway-ui/ui.env   # real token + UI password
```

| Variable | Purpose |
|----------|---------|
| `GATEWAY_API_URL` | Gateway API base (`http://192.168.1.100:8080`) |
| `GATEWAY_API_TOKEN` | Must match piGateway `api.env` |
| `GATEWAY_UI_USER` / `GATEWAY_UI_PASSWORD` | Basic auth for browser |

If migrating from embedded UI in Security-Camera-Central, copy `GATEWAY_*` lines from that repo’s `.env` (install script can do this automatically on first run).

### 3. Run install script

```bash
./deploy/install-pisensors.sh
```

The script:

- Installs app to `/opt/pivpngateway-ui/`
- Creates venv, enables `pivpngateway-ui.service`
- Sets camera API to `127.0.0.1:8002` in Security-Camera-Central `.env`
- Installs nginx site on **port 8000** (`deploy/nginx-pisensors.conf`)
- Restarts `security-camera-central` and `pivpngateway-ui`

### 4. Remove old embedded gateway (one-time)

If gateway code was still in Security-Camera-Central, ensure these are gone (already done on your PiSensors):

- `api/routes/gateway.py`
- `static/gateway/`
- `include_router(gateway.router, …)` in `api/main.py`

### 5. Verify

```bash
curl -s http://127.0.0.1:8001/health
curl -s -o /dev/null -w "%{http_code}\n" -u USER:PASS http://127.0.0.1:8000/gateway/
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8000/docs
ss -tlnp | grep -E ':8000|:8001|:8002'
```

---

## Routine update (UI only)

```bash
cd ~/klasmeier-pi-gateway-ui && git pull
sudo rsync -a app/ static/ /opt/pivpngateway-ui/
sudo systemctl restart pivpngateway-ui
```

Hard-refresh browser (`Ctrl+Shift+R`) after static HTML changes.

---

## Troubleshooting

### Pages hang on :8000

Usually a **stale process on port 8000** (old camera API) or nginx not reloaded:

```bash
./deploy/fix-pisensors.sh
```

### `pivpngateway-ui` crash loop

- Check: `journalctl -u pivpngateway-ui -n 30`
- Ensure `ui.env` is readable by systemd (`640`, `root:pi`)
- App reads env from **systemd** `EnvironmentFile`, not by opening the file in Python

### API calls return 502

- Wrong or placeholder `GATEWAY_API_TOKEN` in `ui.env`
- piGateway API down: `curl http://192.168.1.100:8080/api/health`

### `run_api.sh` / camera API restart loop

Security-Camera-Central must **not** `source .env` in shell (special characters break bash). Use current `run_api.sh` that reads host/port via Python settings.

---

## Paths reference (PiSensors)

| Role | Path |
|------|------|
| Git clone | `~/klasmeier-pi-gateway-ui` |
| Installed app | `/opt/pivpngateway-ui/` |
| UI secrets | `/etc/pivpngateway-ui/ui.env` |
| nginx site | `/etc/nginx/sites-enabled/pivpngateway` |
| systemd | `pivpngateway-ui.service` |
| Camera API | `/home/pi/Security-Camera-Central` → `127.0.0.1:8002` |

---

## Secrets (never in git)

- `/etc/pivpngateway-ui/ui.env` — back up offline with piGateway `api.env`
- `.env` in repo root (dev only, gitignored)

Templates in git: **`deploy/ui.env.example`** only.

---

## Rebuild from scratch

1. Clone this repo on PiSensors.
2. Restore `ui.env` from backup (or copy from `ui.env.example` + match token on `.100`).
3. Run `./deploy/install-pisensors.sh`.
4. Open `http://192.168.1.26:8000/gateway/`.
