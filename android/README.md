# Internet Gateway Path — Android app

Family phone app that shows whether traffic is using **Obscura Internet**, **Home Internet**, or **Phone Internet**.

**Build on PC:** see **[BUILD-PC.md](BUILD-PC.md)** — full steps for Android Studio, USB install, and first-run setup.

**Spec:** `android_app_build_specifications.md` in **klasmeier-pi-gateway** → `project-specifications/`.

## Quick start (PC)

```bash
git clone git@github.com:kklasmeier/klasmeier-pi-gateway-ui.git
cd klasmeier-pi-gateway-ui/android
# Open this folder in Android Studio Quail 1, or:
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Phase status

| Phase | Status |
|-------|--------|
| 1 — Core check, history, manual setup | **Started** |
| 2 — Widget + change alerts | Not started |
| 3 — Admin QR page on PiSensors | Not started (use manual JSON until then) |

## Related

- **klasmeier-pi-gateway** — gateway API (`GET /api/client-path`, `/api/egress`)
- **This repo** — PiSensors web UI; Phase 3 QR generator will live here
