# Handoff

Current release: 0.4.1 (code 6), AbhayShah06/MockLocation.

The UI keeps Start/Stop fixed, separates draft from active coordinates, supports large fonts/compact screens, and hides the update banner except for validated newer releases. Native Android dialogs handle Back; no custom window swipe animation. Saved-place format remains compatible. Map cache is bounded to 100 MB; the user agent identifies the app. Public OSM infrastructure is not an unlimited production tile service.

JDK 17 / SDK 35 / Gradle 8.9. Unit tests, build and lint pass (warnings remain). Native 7-test suites passed locally on API 29 and 36 after menu/intent fixes; API 35 passed before the final compact-layout changes. Final APK is re-signed with the original public 0.3.0 signer, verified against its certificate. Upgrading 0.3.0 → 0.4.1 was tested through Android package installation.

GitHub Actions runs native QA on API 29/31/34/35/36, plus Java CodeQL. Read the actual run results before claiming all matrix jobs passed. Physical Samsung/Motorola firmware, OEM battery management, real GPS/geocoder availability and final installer UI confirmation are not verified by generic emulators. See QA.md.

The old local update.json with PENDING hash was backed up outside the repository. Publish only the exact APK hash and URL. Keep signing keys outside git. No AXL changes.
