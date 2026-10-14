# Mock Location

A personal Android test utility that publishes a chosen coordinate through Android's official **Select mock location app** developer setting. It does not attempt to evade detection, bypass app rules, or modify the device.

## Starter included

- Validated latitude/longitude entry
- Explicit active/inactive state
- Foreground notification while spoofing is active
- One-tap stop that removes the test provider
- Native, plain Android UI

## Hard release plan

1. **Reliability:** verify start/update/stop on Android 10–15, reboot cleanup, permission denial, and process death.
2. **Usability:** map picker, saved places, recent coordinates, movement routes, and a persistent safety stop.
3. **Testing:** unit tests for coordinate validation; device tests proving the provider is removed on stop and failure.
4. **Distribution:** personal sideloaded APK. The user must manually enable Developer options and select this app as the mock provider.

## Build status

The source foundation is present. An APK has not been produced because this machine did not have Java or the Android SDK installed when the project was created.

