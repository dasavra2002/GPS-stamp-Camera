# Review findings and roadmap

## Verification status
- **Compiled cleanly (0 errors)** against the real Android API (android.jar) and the exact CameraX 1.5 public API signatures: everything in `camera/`, `data/` and `stamp/`. That covers the camera controller, manual controls, stamp renderer, photo pipeline, gallery metadata, GPS/weather/map/sensors and Plus Code.
- **Not compiled yet:** the Jetpack Compose screens in `ui/` and `MainActivity.kt`. Compose libraries couldn't be downloaded in the environment where this was written. They were reviewed by hand, but the **first GitHub Actions build is the real test**. If it fails, the log points to the exact line.
- The Plus Code encoder was checked against a real GPS Map Camera photo (`7JXWW5V4+28`).

## Fixed during the review
1. Saved photos and videos could contain the placeholder text "Locating…" or "Loading map…" when captured before the lookups finished. Placeholders are now preview-only.
2. Gallery lookups and saving ran on a screen-scoped coroutine. Leaving the gallery mid-job left photos stuck on "Looking up…" or "Saving…". They now run on an app-level scope.
3. The sound-level meter and the video recorder competed for the microphone, which can mute video audio on some phones. The meter now pauses while recording.
4. Gallery thumbnails ignored EXIF rotation, so portrait photos showed sideways.
5. Altitude was GPS ellipsoid height, which can be tens of metres off sea level. Android 14+ now reports true mean-sea-level altitude.
6. Target SDK raised to 36, which Google Play requires for new apps and updates since 31 Aug 2026.
7. Every CI build used a random debug key, so updates wouldn't install over the old app. Optional permanent signing via GitHub secrets has been added.
8. The activity no longer restarts on rotation or resize. Android 16 ignores the portrait lock on tablets and foldables, and a restart lost the gallery queue.
9. A warning now appears when capturing before GPS has a fix.
10. A small bitmap leak in aspect-ratio cropping was fixed.

## Recommended next (highest value first)

### Reliability and release
- **Device testing matrix.** RAW, HDR video, the HDR extension, manual sensor control and burning the stamp into video differ by phone and lens. Test at least one Pixel, Samsung Galaxy, Xiaomi/Redmi, OnePlus and Vivo/Oppo. The fallbacks are in place, but real-device confirmation matters.
- **ViewModel for state.** The camera mode, manual settings and gallery queue are lost on process death or a dark-mode switch. Moving them into a ViewModel fixes this.
- **Map tile cache.** Add an on-disk cache with an offline fallback. This also respects OpenStreetMap's tile policy. For commercial or heavy use, switch to a keyed provider (MapTiler, Mapbox, or your own tiles).
- **R8 shrinking** for a smaller release APK. It's left off for now because it needs one test build.
- **Automated tests:**
  - unit tests for Plus Code, Kelvin→gains and crop maths
  - a renderer layout test (Robolectric)
  - an instrumented capture smoke test
- **Crash reporting**, for example Firebase Crashlytics, to see OEM-specific camera failures.

### Accuracy and trust
- **Compass true north.** The compass is magnetic north today. Add a True North option (`GeomagneticField` declination).
- **Time source.** Offer GPS/network time instead of the phone clock, plus a "wait for GPS fix before capture" switch.
- **Tamper evidence.** Optionally embed a QR code or signed hash of the stamp data. This helps when stamps are used as proof of site visits.
- **Address language.** Let the user choose the address language (English, Hindi, …) and move to the async Geocoder API on Android 13+.
- **Weather label.** Mark gallery-photo weather as historical (hourly model data) in the stamp.

### Features
- **Stamp existing videos** from the gallery (Media3 Transformer with an overlay).
- **Save original** (unstamped) copy option, as GPS Map Camera offers.
- **Multiple saved stamp templates** with a quick switcher.
- **Capture controls:** volume-key shutter, self-timer, burst, interval/time-lapse.
- **Video tools:**
  - pause/resume and an audio level meter
  - external/USB mic selection and timecode
  - Blackmagic-style zebras, focus peaking and false colour (GPU shader via a CameraX effect)
  - LUT preview
- **Pro photo:** focus/exposure lock (AE-L/AF-L) and exposure bracketing.

### Polish
- **Performance.** The stamp re-renders up to twice a second, allocating a new bitmap each time. Reuse bitmaps and typefaces. Move the gallery editor's preview rendering off the main thread.
- **Battery.** Lower the GPS update rate while on the settings or gallery screens.
- **Localization** (strings into `strings.xml`, with Hindi as a start) and **accessibility** (TalkBack labels, font scaling in the info bar).
- **Branding.** Themed (monochrome) launcher icon and a splash screen.
