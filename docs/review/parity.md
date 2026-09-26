# Smart Flight rewrite: feature, behavior and UI parity review

Static review only. Nothing here was built or run. Each statement is traced to code or to a screenshot.

- Original (reference): `/home/ai-dev/smart-flight`, including screenshots `promo/screen-1.png`, `screen-2.png`, `screen-3.png` and `promo.png`
- Rewrite (under review): `/home/ai-dev/smart-flight-2-modern`

## What the screenshots show

- **`promo/screen-1.png`**
  - Purple toolbar with the title "Smart Flight" centered and a "⋮" overflow menu.
  - Satellite card titled "Connected to 8 satellites". It has a **bar chart**: one bar per satellite, with signal strength on the Y axis (1–22 dB-Hz scale) and the satellite index on the X axis (0–18). Bars are **green (#4caf50) when the satellite is used in the fix** and **red (#f44336) when it is not**.
  - Course card:
    - a cyan abbreviation "W";
    - a large "271°";
    - a **compass rose with N/E/S/W letters around a rotating airplane silhouette**.
  - Flight parameters card: the labels "Speed / Vertical Speed / Altitude" in muted lavender, with "-" placeholders.
- **`promo/screen-2.png`**
  - Location card: "Closest city Kelsterbach / Country Germany / Distance to the city 2.7 km / Actual time 12:26 (+2:00)". Labels are muted and values are light.
  - Route card: "Frankfurt am … San Francisco" in large type, then "Distance 9156.4 km", "Distance to destination 9156.9 km", "Estimated arrival in 11h 42min", and a trash icon.
  - Offline map showing a **curved great-circle route line in dark purple** and a **dark-purple plane marker**.
- **`promo/screen-3.png`**: the city picker.
  - A search field with a cyan underline and a "SEARCH" button.
  - The result "Mountain View, United States".
  - A **large map, centered on the found city, with a purple pin**.
  - A full-width "CONFIRM" button.
- **`promo/promo.png`**: brand art. Indigo (#4a4a9a-ish) background, a teal/cyan accent pattern, and the tagline "Get flight details based on GPS position".
- **Palette** (from `res/values/colors.xml` and `styles.xml`):

  | Role | Color |
  |---|---|
  | page | `purple_dark #484685` |
  | cards / toolbar | `purple_main #5b5999` |
  | accent | `cyan_main #25e5fe` |
  | labels | `text_color_dark #a1a0c4` |
  | values | `text_color_light #d9d9ed` |
  | satellites | `satellite_green` / `satellite_red` |
  | dialogs (`md_background_color`) | `purple_dark` |

## Inventory

| Feature | Original evidence | Rewrite evidence | Status |
|---|---|---|---|
| Satellite signal bar chart (C/N0 per satellite, green/red) | `cards/satellites/SatellitesCardView.kt:46-142`, `colors.xml:13-14`, screen-1.png | `ui/gnss/GnssStatusScreen.kt:366-405` (text list only); `gnss/GnssStatusState.kt:20` (`signalStrengthDbHz` never rendered) | REGRESSION HIGH |
| "Connected to N satellites" (used count) | `SatellitesCardView.kt:138-141` | `GnssStatusScreen.kt:367,383` | MODERNIZATION |
| No-satellites state (Lottie animation, alternating "Move device closer to the window" tip) | `cards/satellites/NoSatellitesFoundView.kt:23-35`, `no_satellites_layout.xml:9-31` | `GnssStatusScreen.kt:302` (static text) | MISSING LOW |
| GPS-disabled prompt | `MainActivityPresenter.kt:22-28`, `common/DialogUtils.kt:27-37` | `GnssStatusScreen.kt:303-310` exists but cannot be reached (`MainActivity.kt:582`) | REGRESSION HIGH |
| Speed / vertical speed / altitude | `cards/gps/FlightParametersCardViewPresenter.kt:73-87` | `flight/FlightParametersController.kt:79-112`, `ui/gnss/FlightParametersCard.kt:136-280` | MODERNIZATION (units correct) |
| Barometric pressure, independent of GPS | `FlightParametersCardViewPresenter.kt:102-113` | `MainActivity.kt:515-526` (only merged into GPS readings) | REGRESSION MEDIUM |
| Vertical-speed smoothing (3-sample average) | `avionic/calculators/VerticalSpeedCalculator.kt:16,49-52` | `FlightParametersController.kt:95-112` (no smoothing) | REGRESSION LOW |
| ft/min conversion | `VerticalSpeedCalculator.kt:39-40` (divides by 1000, which is a bug) | `displayunits/UnitPresentation.kt:37` (correct) | MODERNIZATION (bug fixed; this is a behavior change) |
| Compass heading, abbreviation, GPS bearing | `cards/course/CourseCardViewPresenter.kt:87-111` | `ui/gnss/CourseCard.kt:110-191` | MODERNIZATION, with a rounding difference (LOW) |
| Compass rose (N/E/S/W letters, airplane, animated rotation) | `course_card_layout.xml:48-94`, `CourseCardView.kt:66-82`, screen-1.png | `CourseCard.kt:234-260` (72dp circle with an arrow) | VISUAL_MISMATCH MEDIUM |
| Compass on accelerometer + magnetometer | `avionic/calculators/CourseCalculator.kt:35-69`, `services/SensorService.kt:61-64` | `orientation/OrientationSource.kt:131,136` (rotation vector only) | REGRESSION MEDIUM |
| Hide compass/horizon card when the sensor is missing (blur overlay, choice persisted) | `cards/overlay/BlurUtils.kt:27-38`, `CourseCardView.kt:44-64`, `HorizonCardView.kt:63-72`, `settings/FlightAppPreferences.kt:112-134` | `CourseCard.kt:72`, `HorizonCard.kt:67` (text only) | MISSING LOW |
| Artificial horizon + Calibrate | `cards/horizon/HorizonCardViewPresenter.kt:66-93`, `horizon_card_layout.xml` | `horizon/HorizonController.kt`, `ui/gnss/HorizonCard.kt:174-231` | MODERNIZATION, but calibration behavior changed (LOW) |
| Horizon long-press "reset to absolute" | `HorizonCardView.kt:35`, `HorizonCardViewPresenter.kt:90-93` | not found (searched `onLongClick`/`combinedClickable` in `ui/gnss`) | MISSING LOW |
| Closest city, country, distance, local time + offset | `cards/gps/LocationCardViewPresenter.kt:72-80`, `TimeCalculator.kt:24-43`, screen-2.png | `nearby/NearbyCityController.kt:165-179`, `ui/gnss/NearbyCityCard.kt:86-130` | MODERNIZATION (DST bug fixed) |
| Nearest-city lookup (R-tree index built once, `jsi` jar) | `services/city/FindCityHelper.kt:17-62`, `app/libs/jsi-1.0.0.jar` | `nearby/AndroidNearbyCityRepository.kt:26-54` (full table read per GPS fix) | REGRESSION MEDIUM (performance) |
| Route: destination-only gives remaining distance + ETA | `cards/route/RouteCardViewPresenter.kt:134-144,227-258` | `route/RouteController.kt:183-201` | REGRESSION HIGH |
| Route: distance between cities | `RouteCardViewPresenter.kt:235-242` | `route/RouteModels.kt:77-109` | MODERNIZATION |
| Route: clear one endpoint / clear all | `RouteCardViewPresenter.kt:166-207` | `ui/route/RouteCard.kt:72-78,129-138` | MODERNIZATION |
| Route persisted across launches | `FlightAppPreferences.kt:86-110` | `RouteController.kt:126-181` (new storage, no migration) | REGRESSION MEDIUM (upgrade) |
| City picker: search, results list, long-press on map, confirm | `cards/route/FindCityActivity.kt`, `FindCityPresenter.kt`, screen-3.png | `ui/route/RoutePicker.kt` | present; the map regresses (see findings 9 and 18) |
| Picker map centers on the selected city | `FindCityActivity.kt:204-226` (`animateTo`) | `RoutePicker.kt:190-204` (no centering) | REGRESSION MEDIUM |
| Offline map tiles, zoom 1–6 / 1–9 | `base/BaseMapView.kt:60-78`, `cards/map/MapHelper.kt` | `map/MapArchiveRepository.kt`, `ui/gnss/MapCard.kt:74,261-299` | MODERNIZATION |
| Great-circle route line (purple, 7px) | `cards/map/MapCardView.kt:88-92`, screen-2.png | `MapCard.kt:349-354` (straight, cyan) | REGRESSION MEDIUM |
| Plane marker (plane shape, purple, rotated by compass) | `MapCardView.kt:94-97,163-166`, `MapCardViewPresenter.kt:194-199` | `MapCard.kt:326-337`, `res/drawable/ic_plane_map.xml`, `map/MapState.kt:50` | VISUAL_MISMATCH LOW (+ behavior change) |
| Map "my location", expand/shrink | `MapCardView.kt:71-75,194-218`, `map_card_layout.xml:20-40` | `MapCard.kt:121-150,174-199` | VISUAL_MISMATCH LOW |
| Max-zoom tip with Settings shortcut and highlighted preference | `MapCardViewPresenter.kt:186-192`, `MapCardView.kt:124-140`, `settings/CustomCheckBoxPreference.kt` | `MapCard.kt:113-120` (inline text only) | MISSING LOW |
| Settings: notification, keep screen on, bigger zoom, force portrait, 5 unit lists | `res/xml/app_preferences_layout.xml` | `ui/settings/UnitSettingsScreen.kt` | MODERNIZATION (all present); the colors are a VISUAL_MISMATCH |
| About: version, feedback, rate, disclaimer, OSS list | `DialogUtils.kt:39-65`, `about_app_dialog_layout.xml` | `ui/about/AboutDialog.kt` | MODERNIZATION |
| Permission card; compass and horizon still usable without location permission | `cards/adapter/CardViewContainer.kt:46-64,90-101` | `MainActivity.kt:176,328-346` | REGRESSION MEDIUM |
| Background GPS until first fix, notification, dismiss stops GPS | `services/location/LocationService.kt:187-260`, `common/NotificationBroadcastReceiver.kt` | `monitoring/LocationForegroundService.kt` | present; the notification regresses on API 33+ (see finding 8) |
| Keep screen on / force portrait | `MainActivityPresenter.kt:34-48` | `display/DisplayPreferences.kt:17-30` | MODERNIZATION |
| Polish localization | `res/values-pl/strings.xml`, `preference_strings.xml` | not found (no `res/values-*` directories) | MISSING MEDIUM |
| Color palette / theme | `colors.xml`, `styles.xml:4-16,40-43` | `ui/theme/Theme.kt:36-52`, `ui/theme/Color.kt`, `ui/permission/PermissionColors.kt:6-7` | VISUAL_MISMATCH MEDIUM |
| Launcher icon | `mipmap-*/ic_launcher.png` (white plane on purple square) | `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml` | VISUAL_MISMATCH LOW |
| Toolbar with overflow menu | `activity_main.xml:11-36`, `menu/app_menu.xml` | `GnssStatusScreen.kt:191-267` | VISUAL_MISMATCH LOW |
| Orientation changes keep state | manifest `configChanges="orientation\|screenSize"` (`AndroidManifest.xml:20`) | rewrite `AndroidManifest.xml:25-30` (none declared) | REGRESSION LOW |

## Findings, ordered by severity

### HIGH

**1. REGRESSION, HIGH: the satellite signal-strength bar chart is gone; each satellite is now a text row.**
- Original:
  - `cards/satellites/SatellitesCardView.kt:95-142` draws an MPAndroidChart bar per satellite with its C/N0 (`BarEntry(i, sat.signalStrength)`), colored green or red by `usedInFix`.
  - `setupChart` (`:46-93`) sets up the Y axis with 8 labels from 1 and the X axis by index.
  - Visible in `promo/screen-1.png`.
- Rewrite:
  - `ui/gnss/GnssStatusScreen.kt:388-405` renders one `SatelliteRow` per satellite: "Satellite N" plus "Used for position" or "Not used for position".
  - `GnssSatellite.signalStrengthDbHz` is filled in (`gnss/AndroidGnssStatusPlatform.kt:25`) but never read by any UI code.
- Why it matters:
  - Signal strength per satellite was the app's main "GNSS status" visualization, and it is lost.
  - Each row is at least 48dp + 12dp. With 30–40 visible satellites (typical with multi-constellation GNSS), the card becomes about 2,000dp tall and pushes every other card far down the screen.
  - The green/red color coding is also gone.

**2. REGRESSION, HIGH: users with location/GPS turned off are never told. The dashboard waits forever.**
- Original:
  - `MainActivityPresenter.kt:22-28` calls `showGpsNotEnabledDialog()` every time the activity starts with GPS disabled.
  - `DialogUtils.kt:27-37` shows "Enable GPS", and "Yes" opens the location settings.
- Rewrite:
  - `MainActivity.kt:579-591` (`startObservation`) only calls `gnssStatusController.attachToExternalSession()`. That method unconditionally emits `Waiting` (`gnss/GnssStatusController.kt:52-56`).
  - `GnssStatusController.start()` is the only code that emits `LocationServicesDisabled` (`:25`), and nothing calls it. A grep for `gnssStatusController.` finds only `stop`, `showError` and `attachToExternalSession`.
  - When location is off, the service stops itself immediately (`monitoring/LocationForegroundService.kt:61-65,133-138`), so no data ever arrives.
  - The "Location services are off / Open location settings" UI in `GnssStatusScreen.kt:303-310` is dead code.
  - Related: when location is turned off mid-session, `setEligibilityLostHandler` (`MainActivity.kt:149-152`) calls `gnssStatusController.stop()`, which does not change the state. The satellite card keeps showing stale data.
- Why it matters: GPS is required for every core card. The user sees "Waiting for GPS signal…" indefinitely, with no hint and no shortcut to fix it.

**3. REGRESSION, HIGH: the route shows no remaining distance or ETA when only a destination is set.**
- Original:
  - `RouteCardViewPresenter.kt:134-144`: setting city B calls `setRouteDetailsData()`, which runs `updateRemainingRouteInfo` (distance to B and ETA) and shows the detail labels.
  - Departure A is optional and only adds "Distance" between the cities (`:235-242`).
- Rewrite:
  - `route/RouteController.kt:186-199` publishes `details = null` unless **both** departure and destination are set.
  - `ui/route/RouteCard.kt:50-71` only renders the details when `details` is present.
- Why it matters: "How far to my destination and when do I arrive" works in the original with just a destination. In the rewrite it silently needs a departure city as well.

### MEDIUM

**4. REGRESSION, MEDIUM: barometric pressure is hidden until the first GPS fix.**
- Original: pressure comes straight from the sensor callback into the card (`FlightParametersCardViewPresenter.kt:102-113`), with or without GPS.
- Rewrite:
  - `MainActivity.kt:519-523` merges pressure into the state only when it is already `FlightParametersState.Readings`.
  - While the state is `Waiting`, `FlightParametersCard.kt:81-83` shows only "Waiting for GPS position…".
- Why it matters: on devices with a barometer, users saw pressure right away (indoors, at the gate, or with a weak GPS signal). Now it is hidden until GPS gets a fix.

**5. REGRESSION, MEDIUM: the compass needs a rotation-vector sensor, so devices without a gyroscope lose it.**
- Original: heading comes from the accelerometer plus magnetometer (`CourseCalculator.kt:35-69`, `SensorService.kt:61-62`). The card is only disabled when those features are missing (`CourseCardViewPresenter.kt:33-36`).
- Rewrite: `orientation/OrientationSource.kt:131,136` uses only `TYPE_ROTATION_VECTOR`. `CourseController.kt:23-25` shows "Compass unavailable" when that sensor is missing.
- Why it matters:
  - Rotation vector is a fused sensor that is often missing on budget devices without a gyroscope. The compass worked on those devices before and now shows "unavailable".
  - The original's smoothing (low-pass filter with α=0.97 plus a 10-sample moving average) is also dropped, so the heading may jitter more.
  - The rewrite does compute the heading relative to the display (`OrientationSource.kt:36-64`), which improves landscape use.

**6. VISUAL_MISMATCH, MEDIUM: the compass rose (airplane and N/E/S/W letters) is replaced by a small arrow in a circle.**
- Original:
  - `course_card_layout.xml:48-94`: an airplane silhouette (`plane_icon`, #d9d9ed) with the letters N/W/S/E (`CompassLetter`, 28sp, `text_color_dark`) around it.
  - It rotates with a 200ms linear animation (`CourseCardView.kt:66-82`).
  - Visible in `promo/screen-1.png`.
- Rewrite: `CourseCard.kt:234-260` draws a 72dp outline circle with a three-line arrow, no cardinal letters, and no animated rotation.
- Why it matters:
  - This is the most recognizable visual in the original's first screenshot, and it is reduced to a small generic glyph.
  - The rotation jumps from sample to sample, including across the 359°→0° wrap.

**7. REGRESSION, MEDIUM: compass and horizon disappear without location permission, and so do Settings and About.**
- Original: `CardViewContainer.kt:46-64,90-101` always shows the Course and Horizon cards (they don't need location) below the permission card. The Settings and About menu is always available.
- Rewrite:
  - `MainActivity.kt:176,328-344` renders only `PermissionOnboardingScreen` when permission is not granted.
  - `AboutDialog` is gated on `Granted` (`:346`).
  - Settings is only reachable inside the `Granted` branch (`:180`).
- Why it matters: users who refuse location lose features that never needed it, and cannot reach Settings or About at all.

**8. REGRESSION, MEDIUM: the background "waiting for GPS" notification is effectively gone on Android 13+.**
- Original: `LocationService.kt:187-218,231-259` shows a dismissible notification while GPS keeps searching in the background. Dismissing it stops GPS (`NotificationBroadcastReceiver.kt:15-28`).
- Rewrite:
  - `POST_NOTIFICATIONS` is declared (`AndroidManifest.xml:10`) but never requested at runtime. A grep for `POST_NOTIFICATIONS` finds only the check in `LocationForegroundService.kt:183-185`.
  - `reconcile` (`:115-121`) keeps the foreground GPS service running in the background whenever the preference is on, even when notifications cannot be shown (`showWaiting` becomes false).
- Why it matters:
  - On API 33+ (and minSdk is 31) the user normally never sees the notification. It can't be tapped to return, and it can't be dismissed to stop GPS.
  - GPS keeps running in the background without the visible notification that the original used to tell the user GPS was still on.

**9. REGRESSION, MEDIUM: the route line is straight instead of a great-circle arc, and its color and width changed.**
- Original:
  - `MapCardView.kt:88-92` sets `isGeodesic = true`, color `purple_dark`, width 7.
  - `promo/screen-2.png` shows the curved Frankfurt–San Francisco arc.
- Rewrite: `MapCard.kt:349-354` uses `Polyline(map)` with `Color.CYAN`, the default width, and no geodesic setting. A grep for `Geodesic`/`geodesic` in `app/src/main` finds nothing.
- Why it matters: a straight line on a Mercator map misrepresents a long-haul flight path, which is a feature highlighted in the promo screenshot.
- Improvement: the rewrite adds departure and destination markers (`:355-383`).

**10. REGRESSION, MEDIUM: the city picker map doesn't center on the selected city, and it is much smaller.**
- Original:
  - `FindCityActivity.kt:204-226` calls `mMapController.animateTo(geoPoint)` with haptic feedback when a city is chosen.
  - The map fills most of the screen (`activity_find_city.xml:13-52`, `promo/screen-3.png`).
- Rewrite:
  - `RoutePicker.kt:190-204` only adds or moves a marker. The camera stays at (0,0), zoom 3 (`:169-170`).
  - The map is the last item of a `LazyColumn`, with a minimum height of 180dp (`:116-132`).
- Why it matters: after searching "mountain view", the pin can be off-screen and the user can't visually confirm the choice. The original's main confirmation cue is gone.

**11. VISUAL_MISMATCH, MEDIUM: Settings, About and the city picker use the stock template theme with dynamic wallpaper colors.**
- Original:
  - `styles.xml:4-16`: `colorPrimary` purple_main, `colorAccent` cyan_main, `windowBackground` purple_dark; dialogs use `md_background_color` purple_dark.
  - `styles.xml:40-43`: the settings screen uses light-on-purple text.
- Rewrite:
  - `ui/theme/Theme.kt:36-52` is the Android Studio template: `dynamicColor = true`, with Purple80 and Pink80 from `ui/theme/Color.kt`.
  - `res/values/themes.xml:4` uses `Theme.Material.Light.NoActionBar`, which can flash a white window at launch.
  - The brand colors only exist as hard-coded constants (`PermissionColors.kt:6-7`, `GnssStatusScreen.kt:70`), which the dashboard cards use.
  - `UnitSettingsScreen` (Scaffold/TopAppBar), `AboutDialog` (AlertDialog/Button) and `RoutePicker` (Button/OutlinedTextField) therefore take their colors from the wallpaper-based dynamic scheme, in light or dark mode.
  - The picker also sits on `Color(0xFF211D46)` (`GnssStatusScreen.kt:171`), which is not in the original palette, and its plain `Text` calls set no color.
  - The accent changed from #25e5fe to #6CF0FF for contrast; that part is justifiable.
  - All labels now use #D9D9ED, which loses the original muted-label / light-value hierarchy (`styles.xml:63-88`).
- Why it matters: the app's brand identity breaks on every secondary screen. The picker text may have poor contrast (see "Not verified").

**12. MISSING, MEDIUM: the Polish localization is gone.**
- Original: `res/values-pl/strings.xml` and `res/values-pl/preference_strings.xml` translate every screen.
- Rewrite: there are no `res/values-*` directories.
- Also: `MapCard.kt:188-194` chooses the map button glyph with `description.startsWith("Expand"/"Collapse")`. That check depends on the English text, so any translation would show "◎" on both buttons.
- Why it matters: Polish users, including the author's own locale, get an English-only app.

**13. REGRESSION, MEDIUM: nearest-city lookup reads the whole city database on every GPS fix.**
- Original: `FindCityHelper.kt:17-41` builds an in-memory R-tree once. `findCity` (`:43-62`) is an indexed `nearestN` lookup.
- Rewrite:
  - `AndroidNearbyCityRepository.kt:26-54`: every `findNearest` call opens SQLite, reads all rows of `cities_info` into a list, and computes a haversine distance for each one.
  - It is called for each fix (`MainActivity.kt:508`); fixes are requested every 1s (`flight/AndroidFlightLocationPlatform.kt:41`).
- Why it matters: this means repeated full-table I/O and CPU work, a likely battery drain on long flights. The coalescing in `NearbyCityController.kt:130-163` limits concurrency but not total work.

**14. REGRESSION, MEDIUM: upgrading users lose all their settings and their saved route.**
- Original: everything lives in SharedPreferences `"LocalPrefs"` (`settings/SettingsFragment.kt:18`, `FlightAppPreferences.kt:24`): unit keys `SPEED_UNIT_PREFERENCE_KEY`… with values "1"/"2"/"3", and `city_a_key`/`city_b_key`.
- Rewrite:
  - It reads new files `display_units`, `display_behavior`, `monitoring_behavior` and `route` (`MainActivity.kt:474-488,544`). A grep for `LocalPrefs`, `city_a_key` and `SPEED_UNIT_PREFERENCE_KEY` finds nothing, so there is no migration.
  - `app/build.gradle.kts:17` sets `versionCode = 1` with the same `applicationId`, while the original is at 49 (`app/build.gradle:26`). That version code is too low to install as an update.
- Why it matters: existing users revert to default units and lose their saved route. As configured, the build cannot ship as an update at all.

### LOW

**15. REGRESSION, LOW: the displayed heading is truncated instead of rounded.**
- Original: `CourseCalculator.kt:61-62` uses `roundToInt()`.
- Rewrite: `course/CourseState.kt:16-19` uses `floor`.
- Why it matters: 271.6° shows as 271° instead of 272°.

**16. REGRESSION, LOW: vertical speed has no smoothing, and its first value is blank.**
- Original: a 3-sample moving average (`VerticalSpeedCalculator.kt:16,49-52`). The first sample shows "+0.0".
- Rewrite: the raw delta between two fixes (`FlightParametersController.kt:95-112`). The first sample shows "—".
- Why it matters: noisy GPS altitude makes the value jump more.
- Positive side: the rewrite times samples in nanoseconds (the original used whole seconds), and fixes the ft/min factor (see the MODERNIZATION notes).

**17. REGRESSION, LOW: horizon calibration resets on every resume; long-press reset and input filtering are gone.**
- Original:
  - `HorizonCardViewPresenter.kt:29-30,70-73` calibrates once, on the first sample after the card attaches, and keeps it through pause/resume.
  - Long-press on Calibrate resets the reference to absolute 0 (`:90-93`).
  - A low-pass filter is applied (`Filter.kt`).
- Rewrite: `MainActivity.kt:393` stops and `:589` restarts `HorizonController`. `start()` calls `stop()`, which clears `referencePitchDegrees` (`HorizonController.kt:43-49`), and the long-press action does not exist.
- Why it matters: returning to the app while the phone is tilted silently re-levels the horizon to that tilt.

**18. MISSING, LOW: the option to hide the compass or horizon card on unsupported devices is gone.**
- Original: blurred overlay with "Hide this card?" (`BlurUtils.kt`, `card_overlay_layout.xml`), persisted with `hideCourseCard`/`hideHorizonCard`.
- Rewrite: a permanent "Compass unavailable" or "Horizon unavailable" card (`CourseCard.kt:72`, `HorizonCard.kt:67`).

**19. MISSING, LOW: the searching-for-GPS animation and the "Move device closer to the window" tip are gone.**
- Original: `NoSatellitesFoundView.kt:23-35` alternates the text every 10s; there is a Lottie `loading.json` animation.
- Rewrite: static text (`GnssStatusScreen.kt:302`). `loading.json` was not carried over.

**20. MISSING, LOW: the max-zoom tip no longer leads to Settings.**
- Original:
  - `MapCardViewPresenter.kt:186-192` shows a top snackbar at most 4 times, with a "Settings" action.
  - That action opens Settings with the "Force bigger map zoom" row flashing (`CustomCheckBoxPreference.kt:31-44`).
- Rewrite: a persistent inline caption on the map (`MapCard.kt:113-120`), with no route to the setting.

**21. VISUAL_MISMATCH, LOW: the map plane marker is a cyan star that follows the GPS track, not the compass.**
- Original: `small_plane_icon`, tinted purple_dark (`SmartFlighAppExtensions.kt:20-27`, `MapCardView.kt:94-97`), rotated by the compass azimuth (`MapCardViewPresenter.kt:194-199`).
- Rewrite:
  - `res/drawable/ic_plane_map.xml` is a four-point #00D4FF diamond/star.
  - It rotates by the GPS bearing and falls back to 0° (north) when there is no bearing (`map/MapState.kt:50`).
- Why it matters: using the GPS track is arguably better in flight, but the marker no longer reads as a plane, and it points north when stationary.

**22. VISUAL_MISMATCH, LOW: the map buttons are text glyphs, and expanding barely enlarges the map.**
- Original: drawable icons `ic_expand`/`ic_shrink` (animated swap) and `drawing_pin_icon`. Expanding doubles the card height and scrolls to it (`MapCardView.kt:194-218`).
- Rewrite:
  - The buttons show "↕" and "◎" as text (`MapCard.kt:187-194`).
  - The map height is 240–360dp collapsed and at most 520dp expanded, using a 16:9 then 4:3 ratio of *width* (`:136-150`). On a phone that is about 1.4× taller, not 2×, with no scroll.

**23. VISUAL_MISMATCH, LOW: the route card's large city names, take-off/landing icons and trash icon became a list of text buttons.**
- Original: `route_card_layout.xml` (100dp take-off/landing icons, city names at 22sp side by side, delete icon), `promo/screen-2.png`.
- Rewrite: `RouteCard.kt:100-140` ("Departure: X [Edit] [Clear]"). The function is equivalent (see the MODERNIZATION notes); only the look changed.

**24. VISUAL_MISMATCH, LOW: the header, launcher icon and notification icon changed.**
- Header:
  - Original: a purple_main toolbar bar with a "⋮" overflow (`activity_main.xml:11-36`).
  - Rewrite: a title on the page color with cyan "Settings" and "About" text buttons (`GnssStatusScreen.kt:191-234`).
- Launcher icon:
  - Original: a white plane on a purple square (`mipmap-*/ic_launcher.png`).
  - Rewrite: a new dark #211D46 design (`res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`).
- Notification small icon:
  - Original: `small_plane_icon` (`LocationService.kt:212`).
  - Rewrite: the adaptive `mipmap/ic_launcher` (`LocationForegroundService.kt:235`), which usually renders as a blob in the status bar.

**25. REGRESSION, LOW: rotating the screen (portrait lock off) resets the dashboard state.**
- Original: `AndroidManifest.xml:20` declares `configChanges="orientation|screenSize"`, so rotation keeps the whole UI.
- Rewrite:
  - No `configChanges` is declared (`AndroidManifest.xml:25-30`), and all state is `mutableStateOf` inside `MainActivity`.
  - Rotation recreates the activity. It loses an in-progress city search and selection, map expansion and viewport, and horizon calibration.
  - `onConfigurationChanged` (`MainActivity.kt:380-385`) is dead code.

**26. REGRESSION, LOW: distances use a spherical formula instead of the WGS84 ellipsoid.**
- Original: `Location.distanceBetween` (WGS84 ellipsoid), in `DistanceCalculator.kt:81-89`.
- Rewrite: spherical haversine with R = 6371.0088 km (`NearbyCityController.kt:189-201`).
- Why it matters: long routes can differ by up to about 0.5%, i.e. tens of km on routes like the 9,156 km example.

**27. VISUAL_MISMATCH, LOW: the card order changed.**
- Original code (`CardViewContainer.kt:53-62`): Course, Horizon, Satellites, Flight parameters, Location, Route, Map.
- Rewrite (`GnssStatusScreen.kt:121-166`): GNSS, Flight parameters, Course, Horizon, Nearby, Route, Map.

### MODERNIZATION (acceptable; listed for the record, including behavior changes that need to go in PR descriptions)

- **ft/min fixed.** The original divided by 1000 (`VerticalSpeedCalculator.kt:39-40`, so 1 m/s showed as about 0.2 ft/min). The rewrite uses the correct 196.85 (`UnitPresentation.kt:37`). This is a behavior change.
- **City local time fixed.**
  - The original added `dstSavings` whether or not DST was in effect, and added the GMT offset in *ms* (`TimeCalculator.kt:24-43`).
  - The rewrite uses the `ZoneId` offset (`NearbyCityController.kt:169-178`) and shows "UTC+02:00" instead of "+2:00".
- **Nearest city chosen by true distance.** The original compared planar lat/lng; the rewrite uses haversine (`AndroidNearbyCityRepository.kt:29`). This can pick a different city near the poles or the antimeridian.
- **Artificial horizon redesigned.** The horizon now moves behind a fixed aircraft symbol, with a pitch ladder and a numeric pitch/roll readout (`HorizonCard.kt:128-231`). The original moved the plane over a static half-dark background.
- **Route arrival shown as full date-time.** The rewrite shows a localized date-time in the destination zone plus "HH:MM" (`RouteModels.kt:94-107`). The original showed "HH:mm (Xh Ymin)", "-:- (∞)" at zero speed, and "..." beyond 24h.
- **Background monitoring stops at the first fix.** The rewrite stops background GPS after the first usable fix (`BackgroundMonitoringBridge.kt:91-96`); the original kept GPS running until the app returned to the foreground.
- **Location request settings changed.** The rewrite requests updates every 1s with no minimum distance; the original used 100ms and 10m (`LocationProvider.kt:14-15`).
- **City picker feedback is inline.** "No city found" and "multiple results" appear inline instead of in a dialog. Unlike the original (`FindCityPresenter.kt:154`), a single search result is no longer auto-selected.
- **Settings are in-app.** The full set of original preferences appears as switches and radio dialogs. The About dialog has equivalent content.
- **Minimum SDK raised.** minSdk goes from 21 to 31, which drops Android 5–11 devices. This is a declared project decision.
- **Accessibility improvements.** Semantics, live regions, 48dp touch targets and higher-contrast text were added throughout.

## Not verified

- **City picker contrast.** Whether plain `Text` in `RoutePicker.kt` really renders dark on the #211D46 background depends on the ambient `LocalContentColor`. `Scaffold` or `Surface` with a non-scheme container color normally falls back to the default (black). Check on a device in both light and dark system themes, and with several wallpapers (dynamic color).
- **Map panning inside the scrolling page.** Original `BaseMapView.kt:80-90` explicitly called `requestDisallowInterceptTouchEvent`. The rewrite's `AndroidView` `MapView` sits inside `verticalScroll` (`GnssStatusScreen.kt:119`), and the picker map sits inside a `LazyColumn`. Test vertical map drags on a device.
- **Horizon roll direction.** Whether `rotationZ = visualRollDegrees` (`HorizonCard.kt:187-190`) turns the horizon the correct way for a given bank, and whether pitch sign and remapping are right in all four display rotations. Settle this with a device test or a unit test built from known rotation matrices.
- **Size of the lookup cost.** The actual cost of finding 13 (row count of `cities_info.db`, time per lookup) was not measured. Measure it with a profiler or a JVM benchmark on the asset.
- **Rotation-vector availability.** How common devices without `TYPE_ROTATION_VECTOR` are among the current user base, which determines how many users finding 5 affects. The Play Console device catalog would answer this.
- **Foreground-service start.** Whether `startForegroundService` can fail silently (`MainActivity.kt:569-573` wraps it in `runCatching`) and leave the dashboard stuck in "Waiting" with no error. Test on Android 14+ with restricted background settings.
- **Old screenshots.** The screenshots predate some original features (pressure row, horizon card, GPS bearing line, arrival clock time), so those parts of the inventory come from code only.
