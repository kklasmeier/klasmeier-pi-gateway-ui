# Internet Gateway Path — Android app

Family phone app that shows whether traffic is using **Obscura Internet**, **Home Internet**, or **Phone Internet**.

**Build on PC:** see **[BUILD-PC.md](BUILD-PC.md)** — full steps for Android Studio, USB install, and first-run setup.

**Family install:** see **[INSTALL-FAMILY.md](INSTALL-FAMILY.md)** — sideload APK to other phones without Android Studio.

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
| 1 — Core check, history, QR setup, settings, recalibrate, map, friendly icons | **Done** |
| 2 — Change alerts (WorkManager, NetworkCallback, quiet hours) | **Done** |
| 2b — Glance home-screen widget | **Done** |
| 3 — Admin QR page on PiSensors + family install doc | **Done** |

**Deferred to later:** full history screen, split-tunnel / per-device tokens / iOS.

## Related

- **klasmeier-pi-gateway** — gateway API (`GET /api/client-path`, `/api/egress`)
- **This repo** — PiSensors web UI; mobile setup QR lives under **Mobile app** in admin UI
