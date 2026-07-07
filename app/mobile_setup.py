"""Build Internet Gateway Path phone setup payload + QR image."""
import base64
import io
import json
from typing import Any
from urllib.parse import urlparse

import httpx
import qrcode

from app import config


def _gateway_ip() -> str:
    parsed = urlparse(config.GATEWAY_API_URL)
    if parsed.hostname:
        return parsed.hostname
    return "192.168.1.100"


def _device_label_for_ip(device_ip: str) -> str | None:
    headers: dict[str, str] = {"Accept": "application/json"}
    if config.GATEWAY_API_TOKEN:
        headers["Authorization"] = f"Bearer {config.GATEWAY_API_TOKEN}"
    try:
        with httpx.Client(timeout=10.0) as client:
            resp = client.get(f"{config.GATEWAY_API_URL}/api/devices", headers=headers)
            if resp.status_code != 200:
                return device_ip
            for device in resp.json():
                if device.get("ip") != device_ip:
                    continue
                notes = (device.get("notes") or "").strip()
                hostname = (device.get("hostname") or "").strip()
                if notes:
                    return notes
                if hostname and hostname != "unknown":
                    return hostname
                return device_ip
    except httpx.HTTPError:
        return device_ip
    return device_ip


def build_payload(*, device_label: str | None = None) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "v": 1,
        "gateway_url": config.GATEWAY_API_URL,
        "token": config.GATEWAY_API_TOKEN,
        "home_ssid": config.HOME_WIFI_SSID,
        "gateway_ip": _gateway_ip(),
    }
    if config.IPINFO_TOKEN:
        payload["ipinfo_token"] = config.IPINFO_TOKEN
    if device_label:
        payload["device_label"] = device_label
    return payload


def qr_png_base64(text: str) -> str:
    qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=2)
    qr.add_data(text)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode("ascii")


def mobile_setup_response(*, device_ip: str | None = None, device_label: str | None = None) -> dict[str, Any]:
    label = device_label
    if not label and device_ip:
        label = _device_label_for_ip(device_ip)
    payload = build_payload(device_label=label)
    text = json.dumps(payload, separators=(",", ":"))
    return {
        "payload": payload,
        "payload_json": text,
        "qr_png_base64": qr_png_base64(text),
        "home_ssid_configured": bool(config.HOME_WIFI_SSID),
    }
