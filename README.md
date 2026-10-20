# Mock Location

Pick a place on the map, paste coordinates or a map link, or search for an address. Save the places you use often.

Choose Mock Location under Developer options → Select mock location app, then allow precise location. Start/Stop stays at the bottom. Editing the target takes effect only when you tap Update location. The update banner appears only for newer releases.

Android 8+. No account, root or analytics. Coordinates work offline; map tiles and address search need internet. Android may stop the service after force-stop, reboot or battery restrictions.

Build with JDK 17 and SDK 35: `./gradlew testDebugUnitTest lintDebug assembleDebug`. See [QA](QA.md) for test coverage and device limitations.

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright). Public tiles follow the [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/). A wider rollout needs a tile provider with suitable capacity. Cache is bounded to 100 MB; no bulk map downloading.
