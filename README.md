# Mock Location

A personal Android test utility that publishes a chosen coordinate through Android's official **Select mock location app** developer setting. It does not attempt to evade detection, bypass app rules, or modify the device.

## Included

- Validated latitude/longitude entry with a focused dark interface
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

Target SDK is 35. Build with Android Studio or a local Gradle installation and Android SDK 35. Select the app in Developer options before starting a mock location. The first start asks for precise location and notification permissions.

