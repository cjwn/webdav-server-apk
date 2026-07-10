# WebDAV Server for Android

A self-contained **WebDAV server** that runs on your phone. Built with **Kotlin**, **Jetpack Compose (Material 3)**, an **AMOLED dark UI**, and an embedded **Ktor (CIO)** engine.

Turn your Android device into a **local / offline file sync hub** on your private network — no cloud account required.

---

## What this is (and is not)

| Designed for | Not designed for |
|---|---|
| Phone as a **local file server** on LAN, USB tethering, or your own hotspot | Hosting on the **public internet** |
| Offline-oriented sync and share | Replacing SaaS / hosted WebDAV |
| WebDAV clients on Windows, macOS, iOS, Linux, Android | Full website-style file browsing in a browser |

**Keep the server on a network you control.** Traffic uses HTTP Basic over the LAN (typical for local WebDAV).

---

## Features

- Embedded WebDAV server in a **foreground service** (stays alive with notifications + optional “run in background”)
- **Multi-user** accounts: Admin (global root) vs User (personal home folder)
- **Read-only** users
- In-app **Files** browser with Live refresh, previews (images, audio, text)
- **Request log** for debugging client access
- Browser **download portal** (form login + one-time download tokens; not a full file manager)
- Public `/favicon.ico` for browsers (no login)
- Setup wizard, QR code for the server URL, AMOLED theme

### Supported WebDAV methods

`OPTIONS`, `GET`/`HEAD` (Range), `PUT`, `DELETE`, `MKCOL`, `PROPFIND`, `PROPPATCH`, `COPY`, `MOVE`, `LOCK`/`UNLOCK`

---

## Requirements

| | |
|---|---|
| **Version** | 1.0.0 |
| **Application ID** | `com.localdev.webdavserver` |
| **Min SDK** | 31 (Android 12) |
| **Target / Compile SDK** | 36 (Android 16) |

**To build:** a Unix-like shell (`bash`), `curl` or `wget`, and `unzip`.  
`setup-env.sh` downloads a portable JDK 17, Android SDK packages, and the Gradle wrapper into `./tools` (not committed).

---

## Quick start

```bash
chmod +x setup-env.sh build-debug.sh package.sh
./setup-env.sh        # one-time: JDK + SDK + Gradle → ./tools
./build-debug.sh      # → dist/webdav-debug.apk
```

Install:

```bash
adb install -r dist/webdav-debug.apk
```

### Release build (signed)

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias webdav \
  -keyalg RSA -keysize 2048 -validity 10000

export KEYSTORE_PATH="$PWD/release.jks"
export KEYSTORE_PASS="your-store-password"
export KEY_ALIAS="webdav"
export KEY_PASS="your-key-password"

./build-release.sh    # → dist/webdav-release.apk
```

Never commit keystores or real passwords. See `.env.example` for the signing variable names only.

| Build | Typical size |
|-------|----------------|
| Debug | ~50–70 MB |
| Release (R8 + arm64-only) | ~15–25 MB |

---

## First launch

1. Open the app → **Setup wizard**
2. Create the first account (**Admin**)
3. Grant **Files access** (and optionally notifications + run in background)
4. Pick a **root folder** (or skip and set later in Settings)
5. **Home → Start Server**
6. Connect clients to `http://<phone-ip>:8080/` with HTTP Basic auth

---

## Connect a WebDAV client

| Platform | How |
|---|---|
| **Windows** | File Explorer → This PC → Map network drive → `http://<ip>:8080/` |
| **macOS** | Finder → Go → Connect to Server → `http://<ip>:8080/` |
| **iOS** | Files → … → Connect to Server |
| **Android** | Cx File Explorer / Solid Explorer / similar → WebDAV |
| **Linux** | `gio mount dav://<ip>:8080/` or Nautilus → Other Locations |

Tip: scan the QR code on the Home screen.

### Windows Basic auth over HTTP

If mapping fails, allow Basic auth over non-HTTPS (local use only):

```bat
reg add HKLM\SYSTEM\CurrentControlSet\Services\WebClient\Parameters /v BasicAuthLevel /t REG_DWORD /d 2 /f
```

Then restart the **WebClient** service.

---

## Users & security notes

| Role | WebDAV root |
|------|-------------|
| **Admin** | Global shared root |
| **User** | `{root}/{username}` only |
| **Read-only** | Writes blocked (User role) |

- Passwords stored as **salted SHA-256** in EncryptedSharedPreferences
- Path traversal is rejected server-side
- Last remaining admin cannot be deleted or demoted
- Prefer private Wi‑Fi / hotspot / USB tether; not public internet

---

## Project layout

```
app/src/main/kotlin/com/localdev/webdavserver/
  data/local/       # DataStore, models, encrypted users
  data/server/      # Ktor WebDAV engine + handlers
  domain/           # Repositories & use cases
  presentation/     # Compose UI (Home, Files, Users, Log, Settings, Setup)
  service/          # Foreground service
  di/               # Manual DI (AppModule)
setup-env.sh        # Portable JDK + SDK + Gradle
build-debug.sh      # Debug APK → dist/
build-release.sh    # Signed release APK
package.sh          # Optional source + APK zip
```

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| Port already in use | Settings → Port (e.g. 8081), restart server |
| No root folder | Settings → Root Folder |
| 401 Unauthorized | Check username/password; client must send Basic auth |
| 403 Forbidden | Read-only user wrote, or user has no home folder |
| Server dies in background | Enable **Run in background** + notifications |
| Browser `/favicon.ico` ≠ file on disk | Expected: that URL is the public app icon; use a WebDAV client for storage files |

---

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
