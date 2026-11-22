package com.example.multivideodownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class DownloadUiState(
    val engineReady: Boolean = false,
    val engineInitFailed: Boolean = false,
    val engineVersion: String = "cargando...",
    val statusText: String = "Inicializando motor yt-dlp + FFmpeg...",
    val isUpdatingEngine: Boolean = false,
    val isDownloading: Boolean = false,
    val activeDownloadUrl: String = "",
    val activeQuality: QualityMode = QualityMode.BEST,
    val activeStamp: StampSpec? = null,
    val progress: Float = 0f,
    val etaSeconds: Long = -1L,
    val logOutput: String = "",
    val errorMessage: String? = null,
    val completedDownload: CompletedDownload? = null,
    val queue: List<QueuedItem> = emptyList(),
    val hasCookies: Boolean = false,
    val cookiesLineCount: Int = 0,
    val mediaRefreshTick: Int = 0
)

object DownloadController {
    private const val TAG = "MultiDownloader"
    private const val PREFS_NAME = "mvd_prefs"
    private const val KEY_LAST_YTDLP_UPDATE_MS = "last_ytdlp_update_check_ms"
    private const val KEY_AUTO_UPDATE_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
    private const val COOKIES_FILENAME = "cookies.txt"
    private const val MAX_COOKIES_BYTES = 2 * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initStarted = AtomicBoolean(false)
    private val queueLock = Any()

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private var workerJob: Job? = null
    @Volatile
    private var currentProcessId: String? = null
    @Volatile
    private var cancelRequested: Boolean = false

    fun ensureEngineInitialized(appContext: Context) {
        val ctx = appContext.applicationContext
        refreshCookiesState(ctx)
        if (!initStarted.compareAndSet(false, true)) return

        scope.launch {
            try {
                YoutubeDL.getInstance().init(ctx)
                FFmpeg.getInstance().init(ctx)
                Aria2c.getInstance().init(ctx)

                var version = getInstalledYtDlpVersion()
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastCheckMs = prefs.getLong(KEY_LAST_YTDLP_UPDATE_MS, 0L)
                val needsWeeklyCheck = System.currentTimeMillis() - lastCheckMs >= KEY_AUTO_UPDATE_INTERVAL_MS

                _state.update {
                    it.copy(
                        engineReady = true,
                        engineInitFailed = false,
                        engineVersion = version,
                        statusText = if (needsWeeklyCheck) {
                            "Verificando actualización de yt-dlp ($version)..."
                        } else {
                            "Canal estable / yt-dlp $version"
                        }
                    )
                }

                if (needsWeeklyCheck) {
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
                        version = getInstalledYtDlpVersion()
                        prefs.edit().putLong(KEY_LAST_YTDLP_UPDATE_MS, System.currentTimeMillis()).apply()
                    } catch (uEx: Exception) {
                        Log.w(TAG, "Auto-update semanal de yt-dlp omitido: ${uEx.message}")
                    }
                    _state.update {
                        it.copy(
                            engineVersion = version,
                            statusText = "Canal estable / yt-dlp $version"
                        )
                    }
                }
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "Error inicializando librerías nativas", e)
                _state.update {
                    it.copy(
                        engineInitFailed = true,
                        statusText = "Error al iniciar motor: ${e.localizedMessage}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado al iniciar", e)
                _state.update {
                    it.copy(
                        engineInitFailed = true,
                        statusText = "Error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun updateEngineNow(appContext: Context) {
        val ctx = appContext.applicationContext
        val snap = _state.value
        if (!snap.engineReady || snap.isDownloading || snap.isUpdatingEngine) return

        _state.update {
            it.copy(isUpdatingEngine = true, statusText = "Actualizando yt-dlp...")
        }

        scope.launch {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
                val newVer = getInstalledYtDlpVersion()
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_YTDLP_UPDATE_MS, System.currentTimeMillis())
                    .apply()
                _state.update {
                    it.copy(
                        isUpdatingEngine = false,
                        engineVersion = newVer,
                        statusText = "Canal estable / yt-dlp $newVer"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando yt-dlp", e)
                _state.update {
                    it.copy(
                        isUpdatingEngine = false,
                        statusText = "Ya está al día (${it.engineVersion})"
                    )
                }
            }
        }
    }

    fun importCookies(appContext: Context, sourceUri: Uri): Result<Int> {
        val ctx = appContext.applicationContext
        return runCatching {
            val rawBytes = ctx.contentResolver.openInputStream(sourceUri)?.use { input ->
                val data = input.readBytes()
                if (data.size > MAX_COOKIES_BYTES) {
                    throw IllegalArgumentException("El archivo excede 2 MB.")
                }
                data
            } ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")

            val text = rawBytes.toString(Charsets.UTF_8)
            val validLines = text.lineSequence()
                .map { it.trim() }
                .filter { line -> line.isNotEmpty() && !line.startsWith("#") && line.contains('\t') }
                .count()

            if (validLines <= 0) {
                throw IllegalArgumentException("El archivo no contiene líneas Netscape válidas de cookies.txt.")
            }

            File(ctx.filesDir, COOKIES_FILENAME).writeBytes(rawBytes)
            refreshCookiesState(ctx)
            validLines
        }
    }

    fun clearCookies(appContext: Context) {
        val ctx = appContext.applicationContext
        runCatching {
            val f = File(ctx.filesDir, COOKIES_FILENAME)
            if (f.exists()) f.delete()
        }
        refreshCookiesState(ctx)
    }

    fun refreshCookiesState(appContext: Context) {
        val file = File(appContext.applicationContext.filesDir, COOKIES_FILENAME)
        if (!file.exists() || file.length() == 0L) {
            _state.update { it.copy(hasCookies = false, cookiesLineCount = 0) }
            return
        }
        val count = runCatching {
            file.useLines { seq ->
                seq.map { it.trim() }.count { it.isNotEmpty() && !it.startsWith("#") && it.contains('\t') }
            }
        }.getOrDefault(0)
        _state.update { it.copy(hasCookies = count > 0, cookiesLineCount = count) }
    }

    fun notifyMediaChanged() {
        _state.update { it.copy(mediaRefreshTick = it.mediaRefreshTick + 1) }
    }

    fun clearCompleted() {
        _state.update { it.copy(completedDownload = null, errorMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun enqueueOrStart(appContext: Context, rawUrl: String, quality: QualityMode): Boolean {
        val cleanUrl = cleanIncomingUrl(rawUrl)
        if (cleanUrl.isEmpty() || !cleanUrl.startsWith("http", ignoreCase = true)) {
            return false
        }

        val ctx = appContext.applicationContext
        ensureEngineInitialized(ctx)

        val stamp = detectPlatformStamp(cleanUrl)
            ?: StampSpec("COLA", "★ WEB ★", "EN COLA", EditorialPalette.Ink)
        val newItem = QueuedItem(
            id = System.nanoTime(),
            url = cleanUrl,
            quality = quality,
            stamp = stamp
        )

        val shouldStartLoop: Boolean
        synchronized(queueLock) {
            _state.update {
                it.copy(
                    queue = it.queue + newItem,
                    errorMessage = null
                )
            }
            shouldStartLoop = workerJob?.isActive != true
            if (shouldStartLoop) {
                workerJob = scope.launch {
                    runQueueLoop(ctx)
                }
            }
        }

        if (shouldStartLoop) {
            runCatching {
                val svcIntent = Intent(ctx, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_START
                }
                ContextCompat.startForegroundService(ctx, svcIntent)
            }.onFailure { err ->
                Log.w(TAG, "startForegroundService fallback en memoria: ${err.message}")
            }
        }
        return true
    }

    fun removeFromQueue(id: Long) {
        synchronized(queueLock) {
            _state.update { state ->
                state.copy(queue = state.queue.filterNot { it.id == id })
            }
        }
    }

    fun cancelActive(clearPendingQueue: Boolean = false) {
        cancelRequested = true
        if (clearPendingQueue) {
            synchronized(queueLock) {
                _state.update { it.copy(queue = emptyList()) }
            }
        }
        currentProcessId?.let { pid ->
            runCatching { YoutubeDL.getInstance().destroyProcessById(pid) }
        }
    }

    private suspend fun runQueueLoop(ctx: Context) {
        try {
            // Si la app abrió directo vía Share Intent, esperar a que yt-dlp termine de iniciar
            val readyState = _state.first { it.engineReady || it.engineInitFailed }
            if (readyState.engineInitFailed && !readyState.engineReady) {
                synchronized(queueLock) {
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            queue = emptyList(),
                            errorMessage = "El motor yt-dlp no pudo inicializarse."
                        )
                    }
                }
                return
            }

            while (true) {
                val nextItem: QueuedItem? = synchronized(queueLock) {
                    val cur = _state.value.queue
                    if (cur.isEmpty()) {
                        null
                    } else {
                        val first = cur.first()
                        _state.update {
                            it.copy(
                                queue = cur.drop(1),
                                isDownloading = true,
                                activeDownloadUrl = first.url,
                                activeQuality = first.quality,
                                activeStamp = first.stamp,
                                completedDownload = null,
                                errorMessage = null,
                                progress = 0f,
                                etaSeconds = -1L,
                                logOutput = "",
                                statusText = "Analizando enlace y buscando streams..."
                            )
                        }
                        first
                    }
                }

                if (nextItem == null) break

                executeSingleDownload(ctx, nextItem)
            }
        } finally {
            synchronized(queueLock) {
                workerJob = null
                _state.update {
                    it.copy(
                        isDownloading = false,
                        activeDownloadUrl = "",
                        activeStamp = null
                    )
                }
            }
            DownloadService.requestStop(ctx)
        }
    }

    private fun executeSingleDownload(ctx: Context, item: QueuedItem) {
        val processId = "MultiVideo_${System.currentTimeMillis()}"
        currentProcessId = processId
        cancelRequested = false

        val stagingRoot = File(
            ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.cacheDir,
            "staging"
        )
        val jobDir = File(stagingRoot, "job_${System.nanoTime()}")
        var lastNotifMs = 0L

        try {
            jobDir.mkdirs()

            val isAudioOnly = item.quality == QualityMode.AUDIO
            val twitterResolved = resolveTwitterDirectVideo(item.url, item.quality)
            val effectiveUrl = twitterResolved?.first ?: item.url
            val outputTemplate = if (twitterResolved != null) {
                "${jobDir.absolutePath}/${twitterResolved.second}.%(ext)s"
            } else {
                "${jobDir.absolutePath}/%(title).90s_%(id)s.%(ext)s"
            }

            val cookiesFile = File(ctx.filesDir, COOKIES_FILENAME)
            val request = YoutubeDLRequest(effectiveUrl).apply {
                addOption("--no-mtime")
                addOption("--no-playlist")
                addOption("--no-check-certificates")
                addOption("--prefer-free-formats")
                addOption("--retries", "3")
                addOption("--fragment-retries", "5")
                addOption("--concurrent-fragments", "4")

                if (cookiesFile.exists() && cookiesFile.length() > 0L) {
                    addOption("--cookies", cookiesFile.absolutePath)
                }

                when (item.quality) {
                    QualityMode.BEST -> {
                        addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4] / bv*+ba/b")
                        addOption("--merge-output-format", "mp4")
                    }
                    QualityMode.HD720 -> {
                        addOption(
                            "-f",
                            "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4] / bv*[height<=720]+ba/b[height<=720]/b"
                        )
                        addOption("--merge-output-format", "mp4")
                    }
                    QualityMode.AUDIO -> {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        addOption("--audio-quality", "0")
                    }
                }

                addOption("-o", outputTemplate)
            }

            DownloadService.notifyProgress(
                ctx = ctx,
                title = item.stamp.mainStamp.replace("★", "").trim(),
                percent = 0,
                indeterminate = true,
                queuedExtra = _state.value.queue.size
            )

            YoutubeDL.getInstance().execute(request, processId) { currentProgress, etaInSeconds, line ->
                val clamped = if (currentProgress >= 0f) (currentProgress / 100f).coerceIn(0f, 1f) else _state.value.progress
                _state.update {
                    it.copy(
                        progress = clamped,
                        etaSeconds = etaInSeconds,
                        logOutput = if (line.isNotBlank()) line else it.logOutput
                    )
                }

                val now = System.currentTimeMillis()
                if (now - lastNotifMs >= 450L) {
                    lastNotifMs = now
                    val pct = (clamped * 100).toInt().coerceIn(0, 100)
                    DownloadService.notifyProgress(
                        ctx = ctx,
                        title = item.stamp.mainStamp.replace("★", "").trim(),
                        percent = pct,
                        indeterminate = currentProgress < 0f,
                        queuedExtra = _state.value.queue.size
                    )
                }
            }

            if (cancelRequested) {
                _state.update {
                    it.copy(
                        isDownloading = false,
                        statusText = "Descarga cancelada por el usuario."
                    )
                }
                return
            }

            val downloadedFiles = jobDir.listFiles { file ->
                file.isFile && file.length() > 0 && !file.name.endsWith(".part") && !file.name.endsWith(".ytdl")
            }

            if (!downloadedFiles.isNullOrEmpty()) {
                val savedFile = downloadedFiles.first()
                val cleanTitle = savedFile.nameWithoutExtension
                val sizeMb = String.format(Locale.US, "%.1f MB", savedFile.length() / 1e6)

                val publicUri = if (isAudioOnly) {
                    saveAudioToMediaStore(ctx, savedFile, cleanTitle)
                } else {
                    saveVideoToMediaStore(ctx, savedFile, cleanTitle)
                }

                val destLabel = if (isAudioOnly) "Music / MultiDownloader" else "Movies / MultiDownloader"

                _state.update {
                    it.copy(
                        isDownloading = false,
                        progress = 1f,
                        completedDownload = CompletedDownload(
                            title = savedFile.name,
                            sizeText = sizeMb,
                            uri = publicUri,
                            isAudio = isAudioOnly
                        ),
                        statusText = "Guardado en $destLabel (${savedFile.name})",
                        mediaRefreshTick = it.mediaRefreshTick + 1
                    )
                }

                DownloadService.notifyCompleted(ctx, savedFile.name, sizeMb, publicUri, isAudioOnly)
            } else {
                _state.update {
                    it.copy(
                        isDownloading = false,
                        errorMessage = "El proceso finalizó pero no se generó el archivo."
                    )
                }
            }
        } catch (e: Exception) {
            if (cancelRequested) {
                _state.update {
                    it.copy(
                        isDownloading = false,
                        statusText = "Descarga cancelada por el usuario."
                    )
                }
            } else {
                Log.e(TAG, "Error en descarga", e)
                val err = e.localizedMessage ?: e.message ?: "Error desconocido"
                _state.update {
                    it.copy(
                        isDownloading = false,
                        errorMessage = "El enlace no respondió o requiere actualizar el motor. Verifica que el video sea público.",
                        logOutput = err
                    )
                }
            }
        } finally {
            currentProcessId = null
            runCatching { jobDir.deleteRecursively() }
        }
    }

    private fun getInstalledYtDlpVersion(): String {
        return try {
            val req = YoutubeDLRequest("--version")
            val resp = YoutubeDL.getInstance().execute(req)
            resp.out.trim().ifEmpty { "2026.01.01" }
        } catch (_: Exception) {
            "2026.01.01"
        }
    }
}

/**
 * Expande enlaces acortados de t.co para obtener la URL canónica con /status/<id>.
 */
fun expandShortUrlIfNeeded(rawUrl: String): String {
    if (!rawUrl.contains("://t.co/", ignoreCase = true)) return rawUrl
    var current = rawUrl
    repeat(3) {
        val nextLocation = try {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "MultiVideoDownloader/1.3")
            }
            val code = conn.responseCode
            val loc = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && !loc.isNullOrBlank()) loc else null
        } catch (_: Exception) {
            null
        } ?: return current

        current = if (nextLocation.startsWith("http", ignoreCase = true)) {
            nextLocation
        } else {
            runCatching { URL(URL(current), nextLocation).toString() }.getOrDefault(current)
        }
        if (!current.contains("://t.co/", ignoreCase = true)) return current
    }
    return current
}

private fun pickFxVideoFromArray(videos: JSONArray?, quality: QualityMode): String? {
    if (videos == null || videos.length() == 0) return null
    val vidObj = videos.optJSONObject(0) ?: return null
    var chosenUrl = vidObj.optString("url", "")
    if (quality == QualityMode.HD720) {
        val variants = vidObj.optJSONArray("variants")
        if (variants != null) {
            var bestBitrate = -1
            for (i in 0 until variants.length()) {
                val v = variants.optJSONObject(i) ?: continue
                val ctype = v.optString("content_type", "")
                val vUrl = v.optString("url", "")
                val br = v.optInt("bitrate", 0)
                if (ctype == "video/mp4" && (vUrl.contains("720") || vUrl.contains("600x") || vUrl.contains("480") || br in 1..2500000)) {
                    if (br > bestBitrate) {
                        bestBitrate = br
                        chosenUrl = vUrl
                    }
                }
            }
        }
    }
    return chosenUrl.takeIf { it.startsWith("http", ignoreCase = true) }
}

/**
 * Resuelve enlaces de X/Twitter (t.co, retweets, quote-tweets y posts sensibles +18 / NSFW)
 * usando las APIs públicas de FxTwitter / VxTwitter.
 */
fun resolveTwitterDirectVideo(url: String, quality: QualityMode): Pair<String, String>? {
    val expanded = expandShortUrlIfNeeded(url)
    val tweetId = Regex("""(?:x\.com|twitter\.com|fxtwitter\.com|vxtwitter\.com)/(?:[^/]+/)?status/(\d+)""", RegexOption.IGNORE_CASE)
        .find(expanded)?.groupValues?.getOrNull(1) ?: return null

    try {
        val conn = (URL("https://api.fxtwitter.com/Twitter/status/$tweetId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "MultiVideoDownloader/1.3")
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val tweet = root.optJSONObject("tweet")
            val quote = tweet?.optJSONObject("quote")
            val directVideos = tweet?.optJSONObject("media")?.optJSONArray("videos")
            val chosenDirect = pickFxVideoFromArray(directVideos, quality)
            if (chosenDirect != null) {
                val author = tweet?.optJSONObject("author")?.optString("screen_name", "x_video") ?: "x_video"
                return Pair(chosenDirect, "${author}_${tweetId}")
            }
            val quoteVideos = quote?.optJSONObject("media")?.optJSONArray("videos")
            val chosenQuote = pickFxVideoFromArray(quoteVideos, quality)
            if (chosenQuote != null) {
                val author = quote?.optJSONObject("author")?.optString("screen_name", "x_quote") ?: "x_quote"
                return Pair(chosenQuote, "${author}_${tweetId}")
            }
        }
    } catch (e: Exception) {
        Log.w("MultiDownloader", "FxTwitter fallback warning: ${e.message}")
    }

    try {
        val conn = (URL("https://api.vxtwitter.com/Twitter/status/$tweetId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "MultiVideoDownloader/1.3")
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val author = root.optString("user_screen_name", "x_video")
            val mediaUrls = root.optJSONArray("mediaURLs")
            val firstDirect = (0 until (mediaUrls?.length() ?: 0))
                .mapNotNull { idx -> mediaUrls?.optString(idx, "") }
                .firstOrNull { it.startsWith("http", ignoreCase = true) }
            if (firstDirect != null) {
                return Pair(firstDirect, "${author}_${tweetId}")
            }
            val qrt = root.optJSONObject("qrt")
            val qrtMedia = qrt?.optJSONArray("mediaURLs")
            val firstQrt = (0 until (qrtMedia?.length() ?: 0))
                .mapNotNull { idx -> qrtMedia?.optString(idx, "") }
                .firstOrNull { it.startsWith("http", ignoreCase = true) }
            if (firstQrt != null) {
                val qAuthor = qrt?.optString("user_screen_name", "x_quote") ?: "x_quote"
                return Pair(firstQrt, "${qAuthor}_${tweetId}")
            }
        }
    } catch (e: Exception) {
        Log.w("MultiDownloader", "VxTwitter fallback warning: ${e.message}")
    }

    return null
}

class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        when (intent?.action) {
            ACTION_CANCEL -> {
                DownloadController.cancelActive(clearPendingQueue = true)
                stopForegroundCompat()
                isForegroundActive = false
                stopSelf()
            }
            ACTION_STOP -> {
                stopForegroundCompat()
                isForegroundActive = false
                stopSelf()
            }
            else -> {
                val notification = buildProgressNotification(
                    this,
                    title = "Preparando descarga...",
                    percent = 0,
                    indeterminate = true,
                    queuedExtra = DownloadController.state.value.queue.size
                )
                startForegroundCompat(notification)
                isForegroundActive = true
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isForegroundActive = false
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_START = "com.example.multivideodownloader.ACTION_START"
        const val ACTION_CANCEL = "com.example.multivideodownloader.ACTION_CANCEL"
        const val ACTION_STOP = "com.example.multivideodownloader.ACTION_STOP"

        private const val CHANNEL_PROGRESS = "mvd_downloads"
        private const val CHANNEL_DONE = "mvd_done"
        private const val NOTIFICATION_ID = 4101
        private const val DONE_NOTIFICATION_ID = 4102

        @Volatile
        private var isForegroundActive = false

        fun requestStop(ctx: Context) {
            if (!isForegroundActive) return
            runCatching {
                val intent = Intent(ctx, DownloadService::class.java).apply { action = ACTION_STOP }
                ctx.startService(intent)
            }
        }

        fun notifyProgress(
            ctx: Context,
            title: String,
            percent: Int,
            indeterminate: Boolean,
            queuedExtra: Int
        ) {
            if (!isForegroundActive) return
            ensureChannels(ctx)
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notif = buildProgressNotification(ctx, title, percent, indeterminate, queuedExtra)
            runCatching { nm.notify(NOTIFICATION_ID, notif) }
        }

        fun notifyCompleted(
            ctx: Context,
            fileName: String,
            sizeText: String,
            uri: Uri?,
            isAudio: Boolean
        ) {
            ensureChannels(ctx)
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val tapIntent = if (uri != null) {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, if (isAudio) "audio/*" else "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(ctx, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val pi = PendingIntent.getActivity(
                ctx,
                DONE_NOTIFICATION_ID,
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notif = NotificationCompat.Builder(ctx, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Guardado ($sizeText)")
                .setContentText(fileName)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()

            runCatching { nm.notify(DONE_NOTIFICATION_ID, notif) }
        }

        private fun ensureChannels(ctx: Context) {
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_PROGRESS,
                        "Descargas activas",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Progreso de descargas en segundo plano"
                    }
                )
            }
            if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_DONE,
                        "Descargas completadas",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Aviso cuando un archivo se guarda en Galería"
                    }
                )
            }
        }

        private fun buildProgressNotification(
            ctx: Context,
            title: String,
            percent: Int,
            indeterminate: Boolean,
            queuedExtra: Int
        ): Notification {
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPi = PendingIntent.getActivity(
                ctx,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val cancelIntent = Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            val cancelPi = PendingIntent.getService(
                ctx,
                NOTIFICATION_ID + 10,
                cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val queueSuffix = if (queuedExtra > 0) " · +$queuedExtra en cola" else ""
            val contentTitle = if (indeterminate) "Descargando$queueSuffix" else "Descargando $percent%$queueSuffix"

            return NotificationCompat.Builder(ctx, CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(contentTitle)
                .setContentText(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent.coerceIn(0, 100), indeterminate)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelPi)
                .build()
        }
    }
}
