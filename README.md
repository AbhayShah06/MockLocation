# Mock Location

A personal Android test utility that publishes a chosen coordinate through Android's official **Select mock location app** developer setting. It does not attempt to evade detection, bypass app rules, or modify the device.

## Included

- Validated latitude/longitude entry with a focused dark interface
- Interactive OpenStreetMap picker: tap to select, drag the pin, pan and pinch to zoom; tiles are cached in app storage
- Address/city search runs off the UI thread and offers matching locations; availability depends on the device geocoder and internet
- Coordinate fields, pasted links, saved places and recent targets update the map immediately
- Saved one-tap places and restoration of the last target across updates
- Use the device's last real coordinate as a starting point
- Direct shortcut to Android's required mock-location setup
- Explicit active/inactive state
- Foreground service only while active, with a persistent notification and one-tap stop
- Location refresh every five seconds with saved state for Android service restart recovery
- Provider cleanup on Stop and service errors
- Native, plain Android UI

The app uses Android's official Developer options → Select mock location app setting. It does not use root, hooking, detection bypasses, or analytics. Android may stop a service after force-stop, reboot, or vendor power management; a force-stopped app cannot recover until manually opened again. The notification can only be shown when notifications are allowed.

## Hard release plan

1. **Reliability:** verify start/update/stop on Android 10–15, reboot cleanup, permission denial, and process death.
2. **Usability:** map picker, saved places, recent coordinates, movement routes, and a persistent safety stop.
3. **Testing:** unit tests for coordinate validation; device tests proving the provider is removed on stop and failure.
4. **Distribution:** personal sideloaded APK. The user must manually enable Developer options and select this app as the mock provider.

## Build

Target SDK is 35. Build with Android Studio or a local Gradle installation and Android SDK 35. Select the app in Developer options before starting a mock location. The manifest declares ACCESS_MOCK_LOCATION so Android can list the app in its selector. Start checks that selection and resumes automatically after precise location permission is granted. Notification permission is optional. Internet is used for tiles and address search; no storage permission or map API key is needed. The existing APK updater separately requests install-package access when installing an update.

