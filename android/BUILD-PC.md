# Build on PC — Internet Gateway Path

Use this guide on an **x86_64 Windows, Mac, or Linux PC** with **Android Studio Quail 1** (2026.1.x). The Raspberry Pi cannot build this app natively.

**For Cursor on PC:** open the **`klasmeier-pi-gateway-ui`** repo (or this `android/` folder) and follow the steps below. The gateway API (`GET /api/client-path`) must already be deployed on `192.168.1.100` — it is in the **klasmeier-pi-gateway** repo.

---

## 1. Get the code from GitHub

```bash
git clone git@github.com:kklasmeier/klasmeier-pi-gateway-ui.git
cd klasmeier-pi-gateway-ui
git pull
```

The Android project is in **`android/`**.

Optional — gateway spec and API docs:

```bash
git clone git@github.com:kklasmeier/klasmeier-pi-gateway.git
# Spec: klasmeier-pi-gateway/project-specifications/android_app_build_specifications.md
```

---

## 2. Install Android Studio (one-time)

1. Download **Android Studio Quail 1** from [developer.android.com/studio](https://developer.android.com/studio).
2. Run the installer; accept defaults.
3. First launch: **Standard** setup wizard — install **Android SDK**, **SDK Platform 35**, and **Android SDK Build-Tools**.

---

## 3. Open the project

1. Android Studio → **Open** → select the **`android`** folder inside `klasmeier-pi-gateway-ui`.
2. Wait for **Gradle sync** to finish (first time may take several minutes).
3. If prompted, accept SDK licenses.

**Command-line build** (after Studio has installed the SDK once):

```bash
cd android
./gradlew :app:assembleDebug
```

Windows: `gradlew.bat :app:assembleDebug`

APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

**Install on other family phones:** see **[INSTALL-FAMILY.md](INSTALL-FAMILY.md)**.

---

## 4. Install on your phone

### Option A — USB (recommended for development)

1. On the phone: **Settings → About phone** → tap **Build number** 7 times → enable **Developer options**.
2. **Settings → Developer options** → enable **USB debugging**.
3. Connect phone to PC with USB; accept the **Allow USB debugging** prompt on the phone.
4. In Android Studio: select your phone in the device dropdown → click **Run** (green ▶).

### Option B — Sideload APK

1. Build APK (step 3 above).
2. Copy `app-debug.apk` to the phone (email, Drive, `adb install`, etc.).
3. Open the APK on the phone; allow **Install from unknown sources** if prompted.

**adb install** (if platform-tools installed):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. First-run setup on the phone

Use **home WiFi** so the app can reach the gateway at `192.168.1.100`.

### QR setup (recommended)

1. On a PC browser (home LAN): open **http://192.168.1.26:8000/gateway/#mobile-app** (admin login).
2. On the phone: open **Internet Gateway Path** → **Scan setup QR**.
3. Point the camera at the QR on the admin page.

Nav: **Mobile app** in the gateway admin sidebar. Device detail also has **Show setup QR** for a labeled phone.

**One-time on PiSensors** — add to `/etc/pivpngateway-ui/ui.env`:

```bash
HOME_WIFI_SSID=YourExactWiFiName
IPINFO_TOKEN=your-ipinfo-token
```

Then `cd ~/klasmeier-pi-gateway-ui && ./gitsync.sh && ./deploy.sh`

### Manual setup (fallback)

Expand **Enter manually** in the app, or paste JSON:

Replace placeholders with your values (same token as PiSensors / gateway `api.env`):

```json
{
  "v": 1,
  "gateway_url": "http://192.168.1.100:8080",
  "token": "YOUR_GATEWAY_API_TOKEN",
  "home_ssid": "YOUR_WIFI_SSID",
  "gateway_ip": "192.168.1.100",
  "ipinfo_token": "YOUR_IPINFO_TOKEN"
}
```

| Field | Where to find it |
|-------|------------------|
| `token` | `/etc/pivpngateway/api.env` on piGateway, or `/etc/pivpngateway-ui/ui.env` on PiSensors |
| `home_ssid` | Your WiFi network name (phone WiFi settings) |
| `ipinfo_token` | [ipinfo.io](https://ipinfo.io/account/token) account (optional but recommended) |

Paste the JSON into the **Setup JSON** box → **Import JSON**.

---

## 6. Verify it works

| Test | Expected |
|------|----------|
| Home WiFi, VPN off for your phone | **Home Internet** |
| Home WiFi, VPN on (Obscura routed) | **Obscura Internet** |
| Cellular, WireGuard off | **Phone Internet** |
| Away, home access VPN on | **Obscura Internet** |

Tap **Refresh now** after changing network. Recent path changes appear under **Recent changes**.

### Gateway API sanity check (from PC on LAN)

```bash
curl -s -H "Authorization: Bearer YOUR_TOKEN" \
  http://192.168.1.100:8080/api/client-path | python3 -m json.tool
```

---

## 7. Troubleshooting

| Problem | Fix |
|---------|-----|
| Gradle sync fails | Open **SDK Manager** → install **API 35** and **Build-Tools 35.x** |
| Phone not listed in Studio | Re-plug USB; check USB debugging; try **File → Invalidate Caches** |
| “App not configured” | Complete setup JSON / manual fields on home WiFi |
| “Gateway not reachable” | Phone must be on home WiFi or home access VPN; gateway API is LAN-only |
| “ipinfo check failed” | Check phone has internet; verify `ipinfo_token` if using one |
| Cleartext HTTP error | App allows cleartext to `192.168.1.100` (`usesCleartextTraffic=true`) — should work on debug build |

---

## 8. Repo layout

| Repo | Path | Purpose |
|------|------|---------|
| **klasmeier-pi-gateway-ui** | `android/` | This app |
| **klasmeier-pi-gateway** | `gateway-api/` | Backend API including `/api/client-path` |

---

## 9. Phase status

| Phase | Status |
|-------|--------|
| 1 — Core check, history, manual setup | **In progress** (this build) |
| 2 — Widget + change alerts | Not started |
| 3 — Admin website QR (Mobile app nav) | **Done** — deploy UI on PiSensors |

---

## Cursor handoff prompt

Copy into a new Cursor chat on your PC if helpful:

```text
I'm building the Internet Gateway Path Android app from klasmeier-pi-gateway-ui/android/.
Read android/BUILD-PC.md and android/README.md. Help me Gradle sync, fix build errors,
install the debug APK on my phone via USB, and test setup against gateway 192.168.1.100.
Spec is in klasmeier-pi-gateway/project-specifications/android_app_build_specifications.md.
```
