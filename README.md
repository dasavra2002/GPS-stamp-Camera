# GeoStamp Camera (Android, Kotlin)

A GPS-stamp camera with pro, Blackmagic-style controls. It shoots photos and video from **every camera on the phone**, including RAW (DNG) and 10-bit HDR where the phone supports them. A stamp is burned into each shot: map, address, coordinates, Plus Code, date/time, weather, compass and more. It can also stamp **photos already in your gallery** using their metadata, and you can fill in anything missing.

## Get the APK

### Option A: let GitHub build it (no Android Studio needed)
1. Create a new repository on GitHub and upload everything in this folder. Keep the hidden `.github` folder.
2. Open the repo's **Actions** tab. The **Build APK** workflow runs automatically, or you can click *Run workflow*.
3. When it turns green (about 5–8 minutes), open the run and download **GeoStampCamera-apk** from *Artifacts*.
4. Unzip it, copy the `.apk` to your phone and install it. Allow "Install unknown apps" when asked.

### Option B: Android Studio
Open this folder in Android Studio (Meerkat or newer), wait for Gradle sync, then use **Build → Build APK(s)**, or press Run with your phone connected.

### Keep the same signing key (so updates install over the old version)
Without this, each GitHub build is signed with a random debug key, so you must uninstall before installing a new build. To sign with a permanent key:
1. Create a key once:
   `keytool -genkeypair -v -keystore release.jks -alias geostamp -keyalg RSA -keysize 2048 -validity 10000`
2. Convert it to text: `base64 -w0 release.jks > release.txt` (on Windows: `certutil -encode release.jks release.txt`, then remove the header and footer lines).
3. In the GitHub repo, open **Settings → Secrets and variables → Actions** and add four secrets:
   - `SIGNING_KEYSTORE_BASE64`: the contents of `release.txt`
   - `SIGNING_STORE_PASSWORD`
   - `SIGNING_KEY_ALIAS`: `geostamp`
   - `SIGNING_KEY_PASSWORD`
4. Keep `release.jks` and its passwords safe. Without them you can't publish updates.

## Camera

- **Every lens:** a chip for every camera Android exposes (front, wide, ultra-wide, telephoto, macro), plus zoom presets and pinch zoom. **All lenses** takes one photo from each camera in a row.
- **PHOTO / VIDEO** modes.
- **Info bar (Blackmagic style):** FPS, shutter (angle in video, speed in photo), ISO, EV, white balance in Kelvin with tint, focus, and format. Tap any value to adjust it.
  - Amber values are manual; "A" means automatic.
  - Values shown in Auto are the camera's live readings.
- **Manual controls**, where the lens allows them:
  - ISO
  - shutter speed or shutter angle (180° = natural motion blur)
  - white-balance presets or manual Kelvin + tint
  - manual focus with ∞ and focus lock
  - exposure compensation
- **Photo formats:**
  - JPEG, **RAW + JPEG** (an untouched DNG plus a stamped JPEG) or **RAW only**. A RAW file is sensor data, so it can't carry a burned-in stamp.
  - **HDR:** the phone's HDR mode, or Ultra HDR JPEG on Android 14+. The stamp is kept at normal brightness on HDR screens.
  - **Aspect ratios:** 4:3 and 16:9 from the sensor; 3:2, 1:1, 5:4 and 2.39:1 are cropped, with a crop mask in the viewfinder.
  - **Resolutions:** every size the lens offers, including high-resolution 50/108/200 MP modes.
- **Video:**
  - Resolution from 4K to 480p, 16:9 or 4:3, and the frame rates the lens supports (24/25/30/48/50/60).
  - Bit rate (Auto/Standard/High/Max) and stabilization.
  - **Colour:** SDR, or HLG / HDR10 / HDR10+ / Dolby Vision 10-bit where supported.
  - The **stamp is burned into every video frame**, with the clock ticking. GPS is saved in the file and audio is recorded.
  - Android doesn't give apps RAW video, so video is always compressed (HEVC/H.264).
- **Monitoring:** live histogram (with clipping warnings), level indicator, grid (thirds or crosshair), and frame guides (2.39:1, 2:1, 1.85:1, 1:1, 4:5, 9:16).
- **Fallbacks:** if a lens doesn't support a mode, the app falls back and tells you what it changed. Examples: no RAW on this lens, or the stamp can't go on HDR video, so it records in SDR.
- **Output:** photos go to **Pictures/GeoStamp** (GPS, date and direction also go in the EXIF). Videos go to **Movies/GeoStamp**.

## Stamp items
Each one has its own switch:
- **Location:** map (satellite, street or terrain), place title, country flag, short and full address, lat/long, Plus Code, altitude (true sea level on Android 14+), GPS accuracy.
- **Time:** date and time (5 formats), time zone, numbering.
- **Custom:** logo (your own image), note/hashtag, person name, contact number.
- **Weather and sensors:** temperature, wind, humidity, pressure, compass, magnetic field, sound level, and the lens used.

**Stamp settings:** font, colour, background, size, position (top/bottom), map position (left/right), and map scale.

## Stamp gallery photos
- Pick one or many existing photos. GPS, date/time and direction are read from each photo's EXIF.
- The address and map for that place, and the **historical weather for that date and hour**, are looked up.
- **Edit details** lets you fill in or correct anything:
  - search a place, type lat/long, or use your current location
  - date, time and time zone
  - address, weather and sensor values
- Stamped copies are saved as new files. Originals are never changed.

## Data sources (free, no API keys)
- Address: Android's Geocoder
- Map tiles: Esri (satellite/terrain) and OpenStreetMap (street). Attribution is printed on the map.
- Weather: Open-Meteo (current, recent and historical)
- Plus Code: computed on the phone

For commercial or heavy use, check those providers' terms. OpenStreetMap's public tiles aren't meant for heavy traffic.

## Notes
- Which pro features appear depends on the phone. Manual sensor control, RAW, HDR video and the HDR extension all vary by model and lens; unsupported options are greyed out.
- Kelvin white balance is an approximation computed by the app. For colour-critical work, use RAW.
- Sound level is approximate, because phone microphones aren't calibrated. It pauses during video recording.
- To read GPS from gallery photos, allow the **media location** permission.
- Min Android: 8.0 (API 26). Target: Android 16 (API 36), as Google Play requires since 31 Aug 2026.

See **IMPROVEMENTS.md** for the review findings and roadmap.

## Project layout
```
app/src/main/java/com/geostamp/camera/
  MainActivity.kt              permissions and navigation
  camera/CameraController.kt   lenses, photo/RAW/HDR/video binding, stamp burned into video, histogram
  camera/ManualControls.kt     ISO / shutter / Kelvin WB / focus (Camera2 interop)
  data/                        stamp + camera settings, GPS, sensors, sound, geocoder, weather, map tiles
  stamp/                       stamp renderer, photo pipeline, gallery metadata, Plus Code
  ui/                          camera screen, pro controls, stamp template, gallery + editor
```
