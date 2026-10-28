# MultiVideo Downloader 🎬

A native Android app to download videos and audio from **X (Twitter), TikTok, RedGIFs, Instagram, Facebook, YouTube**, and hundreds of other sites — **100% on-device** using **yt-dlp**, **FFmpeg**, and **aria2c** embedded as native binaries. No cloud, no server, no accounts, no tracking.

<p align="center">
  <img src="https://img.shields.io/badge/Min%20SDK-24-blue" />
  <img src="https://img.shields.io/badge/Target%20SDK-34-green" />
  <img src="https://img.shields.io/badge/Kotlin-1.9.24-purple" />
  <img src="https://img.shields.io/badge/Compose-Material3-orange" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" />
</p>

---

## ✨ Features

| Feature | Details |
|---------|---------|
| **Multi-platform support** | X/Twitter, TikTok, RedGIFs, Instagram, Facebook, YouTube, and 1000+ sites via yt-dlp |
| **Quality selection** | Máxima (1080p+), 720p, Solo Audio (MP3 320kbps) |
| **Native binaries** | yt-dlp + FFmpeg + aria2c bundled — zero external dependencies |
| **Direct to Gallery** | Videos → `Movies/MultiDownloader`, Audio → `Music/MultiDownloader` (MediaStore, scoped storage ready) |
| **Share target** | Send links from any app (browser, X, TikTok, etc.) directly to MultiVideo Downloader |
| **Extractor auto-update** | One-tap button to fetch the latest yt-dlp from GitHub |
| **Download gallery** | Browse, play, and delete your downloads in-app |
| **Brutalist dark UI** | Pure black, high contrast, zero fluff — Material3 + Compose |
| **Offline-first** | Works completely offline after first engine init |

---

## 🏗 Architecture

```
MultiVideoDownloader/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/multivideodownloader/
│   │   │   ├── MainActivity.kt        # Main entry, tabs, downloader screen, engine init
│   │   │   ├── GaleriaScreen.kt       # MediaStore gallery browser (video + audio)
│   │   │   ├── PlayerScreen.kt        # Native VideoView player (landscape)
│   │   │   └── *.kt                   # Helpers: MediaStore save, URL cleaning, quality enum
│   │   ├── res/                       # Drawables, mipmaps, themes
│   │   └── AndroidManifest.xml        # Permissions, activities, share intent-filter
│   ├── build.gradle.kts               # Deps: yt-dlp-android (0.18.1), Compose BOM 2024.06, Coroutines
│   └── ...
├── .github/workflows/main.yml         # Build APK on push/dispatch → upload artifact
├── build.gradle.kts                   # AGP 8.5.2, Kotlin 1.9.24
├── settings.gradle.kts
└── gradle.properties
```

**Key dependencies:**
- `io.github.junkfood02.youtubedl-android:library:0.18.1` — yt-dlp JNI wrapper
- `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1` — FFmpeg binary
- `io.github.junkfood02.youtubedl-android:aria2c:0.18.1` — aria2c binary
- Compose Material3, BOM 2024.06.00
- Coil for async thumbnail loading
- Kotlinx Coroutines

**ABI splits:** Universal APK + per-ABI splits (arm64-v8a, armeabi-v7a) for smaller installs.

---

## 🚀 Build

### Local (Linux/macOS/Windows)
```bash
# Requires JDK 17 + Android SDK (cmdline-tools + platform-34 + build-tools 34.0.0)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk (universal)
# Plus per-ABI splits in app/build/outputs/apk/debug/
```

### CI (GitHub Actions)
Push to `main`/`master` or trigger **Build APK** workflow manually:
- Runs on `ubuntu-latest`
- Sets up JDK 17 (Temurin), Android SDK, Gradle 8.7
- Executes `./gradlew assembleDebug`
- Uploads `app-debug.apk` as artifact

---

## 📱 Usage

1. **Install APK** → Open app → Wait for "Motor listo" (yt-dlp + FFmpeg init ~5-10s first run)
2. **Paste a link** or **Share → MultiVideo Downloader** from any app
3. **Pick quality**: Máxima / 720p / Solo Audio
4. **Tap "Descargar a la Galería"** → Progress shows live yt-dlp output
5. **Done!** File appears in your system Gallery (Google Photos, Files, etc.) under `Movies/MultiDownloader` or `Music/MultiDownloader`
6. **Open "Galería" tab** in-app to browse, play, or delete downloads

---

## ⚙️ Permissions

| Permission | Purpose | SDK |
|------------|---------|-----|
| `INTERNET` | Download streams | All |
| `ACCESS_NETWORK_STATE` | Check connectivity | All |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 29) | Legacy storage | ≤29 |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Legacy read | ≤32 |
| `READ_MEDIA_VIDEO` | Read video library (MediaStore) | 33+ |
| `READ_MEDIA_AUDIO` | Read audio library (MediaStore) | 33+ |
| `POST_NOTIFICATIONS` | Download progress notifications | 33+ |

Scoped storage (API 29+) via MediaStore — no `MANAGE_EXTERNAL_STORAGE` needed.

---

## 🔧 Updating yt-dlp

The embedded yt-dlp binary can become outdated when sites change their APIs.  
Tap **"Actualizar motor (yt-dlp)"** in the app to download the latest stable build from GitHub — no app update required.

---

## 📄 License

MIT — do whatever you want, just keep the license notice.

---

## 🙌 Credits

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — the legendary downloader
- [youtubedl-android](https://github.com/junkfood02/youtubedl-android) — JNI wrapper & binary bundling
- FFmpeg, aria2c — bundled via youtubedl-android
- Jetpack Compose, Material3, Coil, Coroutines