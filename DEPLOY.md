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

## Git workflow (NFS + gitsync — same as Security-Camera-Central)

```text
Pi5Desktop (192.168.1.42)              PiSensors (192.168.1.26)
────────────────────────              ─────────────────────────
NFS mount:                             ~/klasmeier-pi-gateway-ui  (git repo)
  /home/pi/klasmeier-pi-gateway-ui  ◄──  edit in Cursor on desktop
       │
  edit in Cursor
       │
  SSH to PiSensors → ./gitsync.sh     commit / push to GitHub
                                      ./deploy.sh → /opt/pivpngateway-ui
```

### Pi5Desktop — NFS mount (one-time)

On **PiSensors**, export the clone (once): `sudo ./deploy/setup-nfs-export.sh`

Add to `/etc/fstab` (see `deploy/nfs-pi5desktop-fstab.snippet` in klasmeier-pi-gateway repo):

```fstab
192.168.1.26:/home/pi/klasmeier-pi-gateway-ui /home/pi/klasmeier-pi-gateway-ui nfs4 rw,soft,timeo=5,retrans=1,_netdev,nofail,x-systemd.automount,x-systemd.device-timeout=10 0 0
```

```bash
sudo mkdir -p /home/pi/klasmeier-pi-gateway-ui
sudo mount -a
```

Edit files at **`/home/pi/klasmeier-pi-gateway-ui`** on Pi5Desktop (same path as on PiSensors).

### PiSensors — check in

```bash
ssh pi@192.168.1.26
cd ~/klasmeier-pi-gateway-ui
./gitsync.sh
./deploy.sh
```

### Day to day

1. Edit on Pi5Desktop via NFS (Cursor).
2. SSH to PiSensors → `./gitsync.sh` to commit and push.
3. `./deploy.sh` to sync clone → `/opt/pivpngateway-ui` and restart UI.

---

## Git workflow (legacy — Pi5Desktop-only git)

<details>
<summary>Older workflow if not using NFS</summary>

Develop on Pi5Desktop, push from desktop, pull on PiSensors. See git history before NFS setup.
</details>

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
