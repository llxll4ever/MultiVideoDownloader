# VideoDownloader

APK nativo Android para descargar videos de **RedGIFs, Vimeo, YouTube, y más** usando `yt-dlp` + `ffmpeg` embebidos.

## Características

- **UI estilo terminal** — logs crudos de yt-dlp/ffmpeg en tiempo real
- **Binarios embebidos** — yt-dlp (39MB) + ffmpeg (49MB) para arm64, cero dependencias externas
- **Actualización de binarios sin recompilar** — descarga nuevos binarios y guárdalos en `files/bin/`
- **Troubleshooting directo** — ejecuta comandos manuales desde la UI (`[⋮]` → "Comando manual")
- **WorkManager background** — descargas en segundo plano con notificaciones
- **MediaStore** — guarda en galería sin permisos `MANAGE_EXTERNAL_STORAGE` (Android 13+)

## Arquitectura

```
assets/bin/
├── yt-dlp_linux_arm64    # 39MB standalone
└── ffmpeg_arm64          # 49MB static

Runtime (primer launch):
files/bin/  ← copia de assets + chmod +x
```

## Build

```bash
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk (~90MB)
```

## Uso

1. Abre la app → verás terminal con prompt `$`
2. Pega URL (RedGIFs, Vimeo, YouTube, etc.) → Enter
3. Ve logs en vivo: `[download]`, `[ffmpeg]`, `[debug]`, `[info]`
4. Al terminar: `__EXIT_CODE__0` = éxito, archivo en `Downloads/VideoDownloader/`

### Comandos manuales

Menú `[⋮]` → "Comando manual" → ejecuta cualquier cosa:
```
-v "https://redgifs.com/watch/xxx"
-i video.mp4 -c copy out.mp4
--list-formats "https://vimeo.com/xxx"
```

## Actualizar binarios (sin recompilar)

```bash
# yt-dlp
adb push yt-dlp_linux_arm64 /sdcard/
adb shell "cp /sdcard/yt-dlp_linux_arm64 /data/data/com.videodl/files/bin/ && chmod +x /data/data/com.videodl/files/bin/yt-dlp_linux_arm64"

# ffmpeg
adb push ffmpeg /sdcard/
adb shell "cp /sdcard/ffmpeg /data/data/com.videodl/files/bin/ && chmod +x /data/data/com.videodl/files/bin/ffmpeg"
```

O desde la app: descarga binarios nuevos → menú `[⋮]` → "Actualizar binarios" (TODO: implementar UI).

## Solución de problemas

- **RedGIFs no descarga**: usa `-v` para ver extractor, prueba `--extractor-args "redgifs:..."`
- **ffmpeg merge falla**: verifica `--ffmpeg-location` apunta al binario correcto
- **Permisos**: Android 13+ usa `READ_MEDIA_VIDEO`, no `WRITE_EXTERNAL_STORAGE`

## Licencia

MIT — usa yt-dlp/ffmpeg bajo sus respectivas licencias (Unlicense/GPL).
