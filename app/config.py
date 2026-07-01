import os
from pathlib import Path


def _load_env_file(path: Path) -> None:
    if not path.exists():
        return
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, val = line.split("=", 1)
        os.environ.setdefault(key.strip(), val.strip())


# Dev-only local .env; production uses systemd EnvironmentFile=/etc/pivpngateway-ui/ui.env
_load_env_file(Path(__file__).resolve().parents[1] / ".env")

GATEWAY_API_URL = os.environ.get("GATEWAY_API_URL", "http://192.168.1.100:8080").rstrip("/")
GATEWAY_API_TOKEN = os.environ.get("GATEWAY_API_TOKEN", "")
GATEWAY_UI_USER = os.environ.get("GATEWAY_UI_USER", "admin")
GATEWAY_UI_PASSWORD = os.environ.get("GATEWAY_UI_PASSWORD", "gateway")
UI_BIND = os.environ.get("UI_BIND", "127.0.0.1:8001")
