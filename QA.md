# QA — 0.4.1

Build, unit tests and lint pass (warnings, no errors). Native Android 15 tests pass 7/7: Start/Update/Stop with actual mock GPS, background publishing, repeated starts, invalid coordinates with Stop still reachable, app-op revocation/recovery, draft recreation, map tap/zoom, real-location guard, paste/share, saved-place deduplication/rename/delete/cancel, duplicate menus, and valid-newer-only update banners including dismissal, invalid metadata and offline checks.

The shared-link test restores the original Activity intent after delivery so ActivityScenario can track teardown. Its first run failed teardown tracking; the corrected full run passes.

GitHub QA covers API 29/31/34/35/36. Emulator screen profiles approximate the Galaxy S22+, Moto G Stylus 5G (2024), and Motorola One 5G Ace; these are not physical Samsung/Motorola firmware tests. Vendor battery management, actual GPS fixes, geocoder availability, final installer confirmation and real-device predictive-back behavior still need physical-device checks.

No app backend or user account is required. Map cache is bounded to 100 MB. Public OSM tiles have limited capacity: a larger rollout needs an appropriate tile provider, following the [tile policy](https://operations.osmfoundation.org/policies/tiles/). No bulk map downloading.
