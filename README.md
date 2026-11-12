# SSLMS Android App

Native Android app for the Smart Street Light Monitoring System (SSLMS), written in **Kotlin** with **Jetpack Compose**.
It covers all 21 endpoints in the *SSLMS API Integration Reference*. It also has a **demo mode** with sample data
(12 nodes around Dhanbad), so every screen works without the backend.

- Package: `com.sslms.console`, version 1.0.1
- Runs on Android 8.0 (API 26) and newer

## Install the ready-made APK

1. Copy `SSLMS-1.0.1.apk` to the phone (WhatsApp to yourself, Google Drive, or a USB cable).
2. Tap it. If Android asks, allow **Install unknown apps** for the app you opened it from.
3. Open **SSLMS**, choose **Demo data**, and sign in with `admin` / `admin123`.

## Open and build in Android Studio

1. Install a recent Android Studio (it includes the JDK and Android SDK).
2. Unzip, then **File > Open** and select the `sslms-android` folder.
3. Wait for Gradle sync to finish. The first sync downloads dependencies and takes a few minutes.
4. Press **Run ▶** to install on a connected phone or emulator.

From the command line:

```bash
cd sslms-android
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Connect to the real backend

On the sign-in screen choose **SSLMS server** and enter the full API address, including `/api/sslms`:

| Where the app runs | Address to enter |
|---|---|
| Phone on the same Wi-Fi as the server | `http://<SERVER_IP>:<PORT>/api/sslms`, for example `http://192.168.1.20:8080/api/sslms` |
| Android emulator, backend on your computer | `http://10.0.2.2:8080/api/sslms` |
| Server on the internet | `https://your-domain/api/sslms` |

- On a fresh server, use **First-time setup** once to create the admin (`POST /auth/setup`).
- `localhost` on a phone means the phone itself, so always use the server's IP address.
- Plain `http://` is allowed because the doc's backend uses it (`res/xml/network_security_config.xml`).
  Switch the server to HTTPS before production use.

## Screens and the endpoints they use

| Screen | What you can do | Endpoints |
|---|---|---|
| Sign in | Log in, create the first admin, pick server or demo | `POST /auth/login`, `POST /auth/setup` |
| Overview | Network status, map of all nodes, all on/off, latest readings, open faults | `GET /devices`, `GET /devices/{id}/latest`, `GET /devices/telemetry/log`, `GET /devices/faults`, `POST /devices/broadcast`, `POST /devices/{id}/control` |
| Map | Full-screen map, tap a node to open it | same as Overview |
| Devices | Search, filter by state and feeder, add, edit, delete, relay on/off, export CSV | `GET/POST /devices`, `PATCH/DELETE /devices/{id}`, `POST /devices/{id}/control` |
| Device detail | Live reading, Sync now, relay control, history chart (6 h to 30 days), faults | `GET /devices/{id}`, `GET /devices/{id}/latest`, `POST /devices/{id}/syncnow`, `GET /devices/{id}/history`, `GET /devices/faults/{id}` |
| Faults | Open / resolved / all, resolve, delete, export CSV | `GET /devices/faults`, `PATCH /devices/faults/{id}/resolve`, `DELETE /devices/faults/{id}` |
| Reports | Telemetry and fault reports with device and date filters, paging, CSV export | `GET /devices/reports/telemetry`, `GET /devices/reports/faults` |
| Settings | Edit the daily sync times, sign out, reset demo data | `GET /settings`, `PUT /settings` |

## How it's built

- **UI:** Jetpack Compose + Material 3, single activity, Navigation Compose.
- **Networking:** OkHttp + kotlinx.serialization. The JWT is sent as `Authorization: Bearer <token>`; a 401/403 signs out.
- **Map:** osmdroid (OpenStreetMap). No Google Maps API key needed. Tiles are recoloured to the app's night palette.
- **Chart:** drawn on a Compose Canvas (power area plus voltage line); touch to read values.
- **Demo mode:** `DemoApi` implements the same interface as `RemoteApi`, so the whole UI can be tried offline.
- **Fonts:** Barlow and Barlow Condensed (SIL Open Font License), bundled.

```
app/src/main/java/com/sslms/console/
  data/        Models, SslmsApi (interface), RemoteApi (real server), DemoApi, Session
  ui/          SslmsRoot (navigation), theme/, components/ (map, chart, shared UI), screens/
  util/        number/date formatting, CSV sharing
app/src/test/  AppScreenshotTest: walks through the app and saves screenshots
```

## API assumptions to confirm with the backend team

- Lamp on/off comes from `status` in `/devices/{id}/latest` (1 = on, 0 = off). The doc doesn't define it.
- Dates are sent as `YYYY-MM-DDTHH:mm:ss` (Spring `LocalDateTime`).
- Reports are read as Spring `Page<T>` (`content`, `totalElements`, `totalPages`, `number`); plain arrays also work.
- The fault report sends an optional `resolved=true|false` filter.

## Screenshot test (optional)

```bash
./gradlew recordRoborazziDebug
# images: app/build/outputs/roborazzi/
```

## Release build

`./gradlew assembleRelease` makes a minified APK. It is signed with the debug key so it installs directly.
Create your own keystore (**Build > Generate Signed Bundle / APK**) before publishing to the Play Store.
