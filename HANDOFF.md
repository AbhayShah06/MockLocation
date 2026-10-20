# Handoff

## Current state
- Android app in this repository; version 0.4.0, versionCode 5.
- ACCESS_MOCK_LOCATION declaration added. Android 15 emulator lists Mock Location in Select mock location app. Selecting it sets MOCK_LOCATION app-op to allow.
- Start resumes automatically after precise-location permission. Notification denial does not block the service.
- Interactive OpenStreetMap with tap selection, draggable marker, pan/zoom, cached tiles, synchronized coordinates; address search on a worker thread. Saved/recent places and pasted/shared links remain supported.
- Persistent update banner: automatic/manual check, installed-version state, retry, download progress, automatic continuation after install-source permission. Validates manifest, hash, package, version and signer before opening installer.
- Fixed duplicate service update timers, surfaced service errors, added Android 15 system-bar insets, preserved target during recreation.

## Verified
- testDebugUnitTest, assembleDebug and lintDebug pass. Lint has warnings; no errors.
- Coordinate validation/link parser tests plus six update metadata validation tests pass.
- Installed APK on Android 15 emulator. Confirmed app appears in actual Android mock selector and selecting it grants app-op.
- Start opened precise-location permission. Granting While using the app continued automatically into notification prompt.
- Denied notifications: shared preferences active=true; dumpsys location shows GPS and fused coordinates 43.653200,-79.383200 marked mock, owned by this app.
- Map tiles visibly load; update banner reports installed version 0.4.0 and current feed has no newer release.

## Remaining QA / release work
- Latest system-bar inset change compiled/linted but has not been visually rechecked on device.
- Still test stop/provider removal, target updates while active, repeated starts, permission denial/revocation, process death, background behavior, rotation, map tap/drag/pinch, address search success/offline, saved/recent/paste/share, and real-location shortcut end to end. Do not claim all features passed.
- Still exercise new-update availability, download failure/retry, permission return, hash/signature rejection and installer completion end to end. Unit tests cover metadata policy only.
- Remote main/update.json currently offers version 0.3.0. Pre-existing local update.json offers version 0.3.1 with sha256=PENDING; intentionally excluded from this push. Do not publish that file as-is.
- APK is a debug build. To distribute version 0.4.0, use the same signing identity as the installed release, upload the matching APK, then set feed versionCode=5/versionName=0.4.0, exact release URL and real SHA-256. No release or feed publication completed here.
- osmdroid 6.1.20 is archived upstream. Assess maintained alternatives before wider distribution.
- Keep commit/push messages short. No generated attribution or credit lines.

## Build
Use JDK 17 and Android SDK 35. Commands: gradlew.bat testDebugUnitTest assembleDebug lintDebug.
Build output: app/build/outputs/apk/debug/app-debug.apk.
