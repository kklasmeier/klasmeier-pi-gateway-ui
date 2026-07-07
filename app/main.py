"""Standalone Pi Gateway management UI (PiSensors)."""
import os
from typing import Annotated

import httpx
from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.security import HTTPBasic, HTTPBasicCredentials

from app import config, mobile_setup

app = FastAPI(title="Klasmeier Pi Gateway UI", version="1.0.0")
security = HTTPBasic()

STATIC_DIR = os.path.join(os.path.dirname(__file__), "..", "static")


def verify_ui_user(credentials: Annotated[HTTPBasicCredentials, Depends(security)]) -> str:
    import secrets

    ok_user = secrets.compare_digest(credentials.username, config.GATEWAY_UI_USER)
    ok_pass = secrets.compare_digest(credentials.password, config.GATEWAY_UI_PASSWORD)
    if not (ok_user and ok_pass):
        raise HTTPException(
            status_code=401,
            detail="Unauthorized",
            headers={"WWW-Authenticate": "Basic"},
        )
    return credentials.username


def _headers() -> dict[str, str]:
    h = {"Accept": "application/json"}
    if config.GATEWAY_API_TOKEN:
        h["Authorization"] = f"Bearer {config.GATEWAY_API_TOKEN}"
    return h


async def _proxy(method: str, path: str, request: Request) -> Response:
    url = f"{config.GATEWAY_API_URL}{path}"
    body = await request.body() if method in ("POST", "PUT", "PATCH") else None
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.request(
                method,
                url,
                headers=_headers(),
                content=body,
                params=dict(request.query_params),
            )
    except httpx.RequestError as exc:
        raise HTTPException(status_code=502, detail=f"Gateway unreachable: {exc}") from exc
    return Response(
        content=resp.content,
        status_code=resp.status_code,
        media_type=resp.headers.get("content-type"),
    )


def _read_static(name: str) -> HTMLResponse:
    path = os.path.join(STATIC_DIR, name)
    with open(path) as f:
        return HTMLResponse(f.read())


def _static_file(name: str, media_type: str) -> FileResponse:
    path = os.path.join(STATIC_DIR, name)
    if not os.path.isfile(path):
        raise HTTPException(status_code=404, detail="Not found")
    return FileResponse(path, media_type=media_type)


@app.get("/favicon.svg")
def favicon_svg():
    return _static_file("favicon.svg", "image/svg+xml")


@app.get("/favicon.ico")
def favicon_ico():
    return _static_file("icon-192.png", "image/png")


@app.get("/apple-touch-icon.png")
def apple_touch_icon():
    return _static_file("apple-touch-icon.png", "image/png")


@app.get("/icon-192.png")
def icon_192():
    return _static_file("icon-192.png", "image/png")


@app.get("/icon-512.png")
def icon_512():
    return _static_file("icon-512.png", "image/png")


@app.get("/manifest.webmanifest")
def web_manifest():
    return _static_file("manifest.webmanifest", "application/manifest+json")


@app.get("/", response_class=HTMLResponse)
@app.get("", response_class=HTMLResponse)
def gateway_ui(_: str = Depends(verify_ui_user)):
    return _read_static("index.html")


@app.get("/home-vpn/", response_class=HTMLResponse)
@app.get("/home-vpn", response_class=HTMLResponse)
def home_vpn_ui(_: str = Depends(verify_ui_user)):
    return _read_static("vpn.html")


@app.get("/api/mobile-setup")
def get_mobile_setup(
    device_ip: str | None = None,
    device_label: str | None = None,
    _: str = Depends(verify_ui_user),
):
    return mobile_setup.mobile_setup_response(device_ip=device_ip, device_label=device_label)


@app.api_route("/api/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def gateway_api_proxy(path: str, request: Request, _: str = Depends(verify_ui_user)):
    return await _proxy(request.method, f"/api/{path}", request)


@app.api_route("/home-vpn/api/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def home_vpn_api_proxy(path: str, request: Request, _: str = Depends(verify_ui_user)):
    return await _proxy(request.method, f"/api/{path}", request)


@app.get("/health")
def health():
    return {"status": "ok", "gateway_api": config.GATEWAY_API_URL}
