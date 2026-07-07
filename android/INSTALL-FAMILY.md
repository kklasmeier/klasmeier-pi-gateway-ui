# Install on family phones (sideload)

Use this after you have a **release or debug APK** built on a PC (`android/BUILD-PC.md`).

## What you need

- The APK file (`app-debug.apk` for testing, or a signed release APK for long-term family use)
- Each phone on **Android 8+** (app requires API 26+)
- **Home WiFi** for first-time QR setup

## Steps for each phone

1. Copy the APK to the phone (USB, email, Google Drive, etc.).
2. Open the APK file on the phone.
3. If prompted, allow **Install unknown apps** for the app you used to open the file (Files, Chrome, etc.).
4. Tap **Install**.
5. Open **Internet Gateway Path**.
6. On home WiFi, scan the setup QR from the gateway admin site:
   - **http://192.168.1.26:8000/gateway/#mobile-app**
7. Allow **Notifications** when asked (needed for path-change alerts).
8. Optional: add the **Internet Gateway Path** home-screen widget (long-press wallpaper → Widgets).

## After install

| Check | Expected |
|-------|----------|
| Home WiFi, VPN off | Home Internet |
| VPN on (Obscura) | Obscura Internet |
| Cellular only | Phone Internet |
| Widget | Matches app after refresh |

## Updating

Build a new APK on the PC, copy to the phone, and install over the old app (`adb install -r` or open the new APK). Setup and history are kept unless you use **Clear setup** in Settings.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| “Blocked by Play Protect” | Tap **Install anyway** — app is not on Play Store |
| Setup fails | Must be on home WiFi; rescan QR from admin **Mobile app** page |
| No notifications | Settings → Apps → Internet Gateway Path → Notifications → On |
| Wrong path label | Settings → **Recalibrate** on home WiFi |

## Building a release APK (optional)

For family phones without a PC attached each time:

```bash
cd android
gradlew.bat :app:assembleRelease
```

Sign the APK with your keystore (Android Studio → Build → Generate Signed Bundle/APK) before wide distribution. Debug APKs work fine for household testing.
