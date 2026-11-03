package com.example.multivideodownloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Paleta Editorial Industrial (multivideo-prototipo.html)
object EditorialPalette {
    val Paper = Color(0xFFE9EBE1)
    val Raised = Color(0xFFF4F5EC)
    val Ink = Color(0xFF14170F)
    val InkSoft = Color(0xFF3C4136)
    val Muted = Color(0xFF626858)
    val Line = Color(0xFFD2D5C8)
    val LineStrong = Color(0xFFB5B9AA)
    val Brick = Color(0xFFC7462B)
    val BrickDark = Color(0xFF8E2C17)
    val BrickTint = Color(0x1AC7462B)
    val PaperInk = Color(0xFFFBF7EC)
    val Success = Color(0xFF2E6B45)
    val SuccessTint = Color(0x142E6B45)
    val Danger = Color(0xFFA92F1F)
    val DangerSurface = Color(0xFFFBEAE6)
    val DisabledBg = Color(0xFFC9CCC0)
    val DisabledFg = Color(0xFF6E7366)
}

fun editorialColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = EditorialPalette.Brick,
        onPrimary = EditorialPalette.PaperInk,
        primaryContainer = EditorialPalette.Ink,
        onPrimaryContainer = EditorialPalette.PaperInk,
        secondary = EditorialPalette.Ink,
        onSecondary = EditorialPalette.PaperInk,
        tertiary = EditorialPalette.Success,
        background = EditorialPalette.Paper,
        onBackground = EditorialPalette.Ink,
        surface = EditorialPalette.Raised,
        onSurface = EditorialPalette.Ink,
        surfaceVariant = EditorialPalette.Raised,
        onSurfaceVariant = EditorialPalette.Muted,
        outline = EditorialPalette.LineStrong,
        error = EditorialPalette.Danger,
        onError = EditorialPalette.PaperInk
    )
}

enum class TabItem(val label: String, val icon: ImageVector) {
    DOWNLOADS("MESA", Icons.Default.Download),
    GALLERY("GALERÍA", Icons.Default.VideoLibrary),
    PLAYER("REPRODUCTOR", Icons.Default.PlayCircle)
}

data class CompletedDownload(
    val title: String,
    val sizeText: String,
    val uri: Uri?,
    val isAudio: Boolean
)

class MainActivity : ComponentActivity() {

    private var sharedUrlState = mutableStateOf("")
    private var engineReadyState = mutableStateOf(false)
    private var engineVersionState = mutableStateOf("cargando...")
    private var statusMessageState = mutableStateOf("Inicializando motor yt-dlp + FFmpeg...")
    private var selectedTab = mutableStateOf(TabItem.DOWNLOADS)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNeededPermissions()
        handleIncomingShareIntent(intent)

        Thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                Aria2c.getInstance().init(applicationContext)

                var version = getInstalledYtDlpVersion()

                runOnUiThread {
                    engineReadyState.value = true
                    engineVersionState.value = version
                    statusMessageState.value = if (version.startsWith("2025.")) {
                        "Actualizando yt-dlp ($version) en 2do plano..."
                    } else {
                        "Canal estable / yt-dlp $version"
                    }
                }

                if (version.startsWith("2025.")) {
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(
                            applicationContext,
                            YoutubeDL.UpdateChannel.STABLE
                        )
                        version = getInstalledYtDlpVersion()
                        runOnUiThread {
                            engineVersionState.value = version
                            statusMessageState.value = "Canal estable / yt-dlp $version"
                        }
                    } catch (uEx: Exception) {
                        Log.w("MultiDownloader", "Auto-update de yt-dlp omitido: ${uEx.message}")
                    }
                }
            } catch (e: YoutubeDLException) {
                Log.e("MultiDownloader", "Error inicializando librerías nativas", e)
                runOnUiThread {
                    statusMessageState.value = "Error al iniciar motor: ${e.localizedMessage}"
                }
            } catch (e: Exception) {
                Log.e("MultiDownloader", "Error inesperado al iniciar", e)
                runOnUiThread {
                    statusMessageState.value = "Error: ${e.localizedMessage}"
                }
            }
        }.start()

        setContent {
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    window.statusBarColor = EditorialPalette.Paper.toArgb()
                    window.navigationBarColor = EditorialPalette.Raised.toArgb()
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = true
                    insetsController.isAppearanceLightNavigationBars = true
                }
            }

            MaterialTheme(colorScheme = editorialColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = EditorialPalette.Paper
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Topbar Editorial idéntica al prototipo
                        EditorialTopBar(
                            engineReady = engineReadyState.value,
                            engineVersion = engineVersionState.value
                        )

                        // Contenido principal con weight(1f) para no ocultar la barra inferior
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when (selectedTab.value) {
                                TabItem.DOWNLOADS -> DownloaderScreen(
                                    initialUrl = sharedUrlState.value,
                                    engineReady = engineReadyState.value,
                                    engineVersion = engineVersionState.value,
                                    initialStatus = statusMessageState.value,
                                    onVersionUpdated = { newVer ->
                                        engineVersionState.value = newVer
                                    },
                                    onOpenGallery = {
                                        selectedTab.value = TabItem.GALLERY
                                    }
                                )
                                TabItem.GALLERY -> GaleriaScreen()
                                TabItem.PLAYER -> PlayerLauncherScreen(
                                    onBack = { selectedTab.value = TabItem.DOWNLOADS },
                                    onGoGallery = { selectedTab.value = TabItem.GALLERY }
                                )
                            }
                        }

                        // Barra de navegación inferior estilo Editorial
                        EditorialBottomBar(
                            selectedTab = selectedTab.value,
                            onSelectTab = { selectedTab.value = it }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun getInstalledYtDlpVersion(): String {
        return try {
            val req = YoutubeDLRequest("--version")
            val resp = YoutubeDL.getInstance().execute(req)
            resp.out.trim().ifEmpty { "2025.11.12" }
        } catch (_: Exception) {
            "2025.11.12"
        }
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { rawText ->
                val clean = cleanIncomingUrl(rawText)
                sharedUrlState.value = clean
                selectedTab.value = TabItem.DOWNLOADS
            }
        }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VIDEO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }
}

@Composable
fun EditorialTopBar(
    engineReady: Boolean,
    engineVersion: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorialPalette.Paper)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sello ladrillo "MV"
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EditorialPalette.Brick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MV",
                    color = EditorialPalette.PaperInk,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Wordmark MULTI / VIDEO DOWNLOADER
            Column {
                Text(
                    text = "MULTI",
                    color = EditorialPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = "VIDEO DOWNLOADER",
                    color = EditorialPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Engine Pill (LISTO / INICIANDO)
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EditorialPalette.Raised)
                    .border(1.dp, EditorialPalette.Line, RoundedCornerShape(2.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (engineReady) EditorialPalette.Success else EditorialPalette.Brick)
                )
                Text(
                    text = if (engineReady) "LISTO" else "CARGANDO",
                    color = EditorialPalette.InkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = EditorialPalette.Line)
    }
}

@Composable
fun EditorialBottomBar(
    selectedTab: TabItem,
    onSelectTab: (TabItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorialPalette.Raised)
    ) {
        HorizontalDivider(thickness = 1.dp, color = EditorialPalette.LineStrong)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem.values().forEach { tab ->
                val selected = selectedTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (selected) EditorialPalette.Brick else Color.Transparent)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) EditorialPalette.Ink else EditorialPalette.Muted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        color = if (selected) EditorialPalette.Ink else EditorialPalette.Muted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 9.sp,
                        letterSpacing = 0.9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerLauncherScreen(
    onBack: () -> Unit,
    onGoGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialPalette.Paper)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, EditorialPalette.LineStrong, RoundedCornerShape(2.dp))
                .background(EditorialPalette.Raised, RoundedCornerShape(2.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = EditorialPalette.Brick,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "REPRODUCTOR DE MEDIOS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = EditorialPalette.Brick
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reproduce tus descargas sin salir",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EditorialPalette.Ink
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Toca cualquier video o audio en la lista de Recientes o en la Galería para abrirlo con el reproductor del sistema.",
                    fontSize = 13.sp,
                    color = EditorialPalette.InkSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(EditorialPalette.Ink, RoundedCornerShape(2.dp))
                            .clickable { onGoGallery() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Abrir Galería",
                            color = EditorialPalette.PaperInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(1.dp, EditorialPalette.Ink, RoundedCornerShape(2.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ir a Mesa",
                            color = EditorialPalette.Ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

fun cleanIncomingUrl(raw: String): String {
    val regex = Regex("""(https?://\S+)""")
    val match = regex.find(raw)?.value ?: raw.trim()
    return match.trimEnd('/', ' ', '\n', '\r')
}

/**
 * Resuelve enlaces de X/Twitter (incluyendo tweets sensibles +18 / NSFW o retweets que
 * el extractor sin cookies de yt-dlp bloquea con "No video could be found in this tweet")
 * usando las APIs públicas de FxTwitter / VxTwitter.
 */
fun resolveTwitterDirectVideo(url: String, quality: QualityMode): Pair<String, String>? {
    val tweetId = Regex("""(?:x\.com|twitter\.com|fxtwitter\.com|vxtwitter\.com)/(?:[^/]+/)?status/(\d+)""", RegexOption.IGNORE_CASE)
        .find(url)?.groupValues?.getOrNull(1) ?: return null

    // 1. Intento vía FxTwitter API
    try {
        val conn = (java.net.URL("https://api.fxtwitter.com/Twitter/status/$tweetId").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "MultiVideoDownloader/1.1")
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(body)
            val tweet = root.optJSONObject("tweet")
            val author = tweet?.optJSONObject("author")?.optString("screen_name", "x_video") ?: "x_video"
            val videos = tweet?.optJSONObject("media")?.optJSONArray("videos")
            if (videos != null && videos.length() > 0) {
                val vidObj = videos.getJSONObject(0)
                var chosenUrl = vidObj.optString("url", "")
                if (quality == QualityMode.HD720) {
                    val variants = vidObj.optJSONArray("variants")
                    if (variants != null) {
                        var bestBitrate = -1
                        for (i in 0 until variants.length()) {
                            val v = variants.getJSONObject(i)
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
                if (chosenUrl.startsWith("http")) {
                    return Pair(chosenUrl, "${author}_${tweetId}")
                }
            }
        }
    } catch (e: Exception) {
        Log.w("MultiDownloader", "FxTwitter fallback warning: ${e.message}")
    }

    // 2. Respaldo vía VxTwitter API
    try {
        val conn = (java.net.URL("https://api.vxtwitter.com/Twitter/status/$tweetId").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "MultiVideoDownloader/1.1")
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(body)
            val author = root.optString("user_screen_name", "x_video")
            val mediaUrls = root.optJSONArray("mediaURLs")
            if (mediaUrls != null && mediaUrls.length() > 0) {
                val chosenUrl = mediaUrls.optString(0, "")
                if (chosenUrl.startsWith("http")) {
                    return Pair(chosenUrl, "${author}_${tweetId}")
                }
            }
        }
    } catch (e: Exception) {
        Log.w("MultiDownloader", "VxTwitter fallback warning: ${e.message}")
    }

    return null
}

enum class QualityMode(
    val shortTitle: String,
    val badge: String,
    val headerNote: String,
    val footerNote: String
) {
    BEST("Máxima", "MP4", "MEJOR VIDEO + AUDIO", "MP4 / mejor video + audio disponibles"),
    HD720("1080p / 720p", "MP4", "HASTA 720P-1080P", "MP4 / descarga más liviana y rápida"),
    AUDIO("Audio", "MP3", "SOLO AUDIO", "MP3 / alta fidelidad, solo audio")
}

@Composable
fun DownloaderScreen(
    initialUrl: String,
    engineReady: Boolean,
    engineVersion: String,
    initialStatus: String,
    onVersionUpdated: (String) -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var inputFocused by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(QualityMode.BEST) }
    var isDownloading by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var etaSeconds by remember { mutableLongStateOf(-1L) }
    var statusText by remember(initialStatus) { mutableStateOf(initialStatus) }
    var logOutput by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var completedDownload by remember { mutableStateOf<CompletedDownload?>(null) }
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = RECIENTES, 1 = EN COLA
    var recentMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    val processId = remember { "MultiVideoDownload_${System.currentTimeMillis()}" }

    LaunchedEffect(completedDownload, isDownloading) {
        withContext(Dispatchers.IO) {
            val items = queryDownloadedMedia(context)
            withContext(Dispatchers.Main) {
                recentMedia = items
            }
        }
    }

    fun triggerDownload() {
        val targetUrl = cleanIncomingUrl(urlInput.trim())
        if (targetUrl.isEmpty() || !targetUrl.startsWith("http")) {
            errorMessage = "Pega un enlace válido que empiece con https://..."
            return
        }

        errorMessage = null
        completedDownload = null
        isDownloading = true
        progress = 0f
        etaSeconds = -1L
        statusText = "Analizando enlace y buscando streams..."
        logOutput = ""

        scope.launch(Dispatchers.IO) {
            try {
                val stagingDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
                    "staging"
                )
                if (!stagingDir.exists()) {
                    stagingDir.mkdirs()
                }
                stagingDir.listFiles()?.forEach { it.delete() }

                val isAudioOnly = selectedQuality == QualityMode.AUDIO
                val twitterResolved = resolveTwitterDirectVideo(targetUrl, selectedQuality)
                val effectiveUrl = twitterResolved?.first ?: targetUrl
                val outputTemplate = if (twitterResolved != null) {
                    "${stagingDir.absolutePath}/${twitterResolved.second}.%(ext)s"
                } else {
                    "${stagingDir.absolutePath}/%(title).90s_%(id)s.%(ext)s"
                }

                val request = YoutubeDLRequest(effectiveUrl).apply {
                    addOption("--no-mtime")
                    addOption("--no-playlist")
                    addOption("--no-check-certificates")
                    addOption("--prefer-free-formats")
                    addOption("--retries", "3")
                    addOption("--fragment-retries", "5")
                    addOption("--concurrent-fragments", "4")

                    when (selectedQuality) {
                        QualityMode.BEST -> {
                            addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4] / bv*+ba/b")
                            addOption("--merge-output-format", "mp4")
                        }
                        QualityMode.HD720 -> {
                            addOption("-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4] / bv*[height<=720]+ba/b[height<=720]/b")
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

                YoutubeDL.getInstance().execute(
                    request,
                    processId
                ) { currentProgress, etaInSeconds, line ->
                    scope.launch(Dispatchers.Main) {
                        if (currentProgress >= 0f) {
                            progress = (currentProgress / 100f).coerceIn(0f, 1f)
                        }
                        etaSeconds = etaInSeconds
                        if (line.isNotBlank()) {
                            logOutput = line
                        }
                    }
                }

                val downloadedFiles = stagingDir.listFiles { file ->
                    file.isFile && file.length() > 0 && !file.name.endsWith(".part") && !file.name.endsWith(".ytdl")
                }

                if (!downloadedFiles.isNullOrEmpty()) {
                    val savedFile = downloadedFiles.first()
                    val cleanTitle = savedFile.nameWithoutExtension
                    val sizeMb = String.format(Locale.US, "%.1f MB", savedFile.length() / 1e6)

                    val publicUri = if (isAudioOnly) {
                        saveAudioToMediaStore(context, savedFile, cleanTitle)
                    } else {
                        saveVideoToMediaStore(context, savedFile, cleanTitle)
                    }

                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        progress = 1f
                        completedDownload = CompletedDownload(
                            title = savedFile.name,
                            sizeText = sizeMb,
                            uri = publicUri,
                            isAudio = isAudioOnly
                        )
                        statusText = "Guardado en Descargas / MultiVideo (${savedFile.name})"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        errorMessage = "El proceso finalizó pero no se generó el archivo."
                    }
                }
            } catch (e: Exception) {
                Log.e("MultiDownloader", "Error en descarga", e)
                val err = e.localizedMessage ?: e.message ?: "Error desconocido"
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    errorMessage = "El enlace no respondió o requiere actualizar el motor. Verifica que el video sea público."
                    logOutput = err
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialPalette.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // Eyebrow: MESA DE DESCARGA + línea horizontal
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "MESA DE DESCARGA",
                color = EditorialPalette.Brick,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 1.dp,
                color = EditorialPalette.LineStrong
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hero Headline
        Text(
            text = "Baja el video.\nSin rodeos.",
            color = EditorialPalette.Ink,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
            letterSpacing = (-1.2).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Lede
        Text(
            text = "Pega un enlace de Instagram, X, TikTok o Facebook. Se guarda en Descargas, en la mejor calidad disponible.",
            color = EditorialPalette.InkSoft,
            fontSize = 13.5.sp,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // SECCIÓN 1: ENLACE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "ENLACE",
                color = EditorialPalette.Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp
            )
            Text(
                text = if (urlInput.isNotBlank()) "ENLACE LISTO" else "LISTO PARA PEGAR",
                color = EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        // Campo de texto estilo Editorial (con icono, input monospace, botón X y botón PEGAR integrado)
        val fieldBorderColor = when {
            errorMessage != null -> EditorialPalette.Danger
            inputFocused -> EditorialPalette.Ink
            else -> EditorialPalette.LineStrong
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(EditorialPalette.Raised, RoundedCornerShape(2.dp))
                .border(1.dp, fieldBorderColor, RoundedCornerShape(2.dp))
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = EditorialPalette.Muted,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (urlInput.isEmpty()) {
                    Text(
                        text = "https://...",
                        color = EditorialPalette.Muted.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        if (errorMessage != null) errorMessage = null
                    },
                    enabled = !isDownloading,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = EditorialPalette.Ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(EditorialPalette.Brick),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { inputFocused = it.isFocused }
                )
            }

            if (urlInput.isNotEmpty() && !isDownloading) {
                IconButton(
                    onClick = {
                        urlInput = ""
                        errorMessage = null
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Borrar enlace",
                        tint = EditorialPalette.Muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Divisor vertical + botón PEGAR
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(EditorialPalette.Line)
            )

            Box(
                modifier = Modifier
                    .height(42.dp)
                    .clickable(enabled = !isDownloading) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!pasted.isNullOrBlank()) {
                            urlInput = cleanIncomingUrl(pasted)
                            errorMessage = null
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PEGAR",
                    color = EditorialPalette.Brick,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = errorMessage ?: "Acepta enlaces públicos. Sin playlists.",
            color = if (errorMessage != null) EditorialPalette.Danger else EditorialPalette.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // SECCIÓN 2: CALIDAD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "CALIDAD",
                color = EditorialPalette.Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp
            )
            Text(
                text = selectedQuality.headerNote,
                color = EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QualityMode.values().forEach { mode ->
                val isSelected = selectedQuality == mode
                val bg = if (isSelected) EditorialPalette.Ink else EditorialPalette.Raised
                val borderCol = if (isSelected) EditorialPalette.Ink else EditorialPalette.Line
                val titleCol = if (isSelected) EditorialPalette.PaperInk else EditorialPalette.Ink
                val subCol = if (isSelected) EditorialPalette.PaperInk.copy(alpha = 0.65f) else EditorialPalette.Muted

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(2.dp))
                        .clickable(enabled = !isDownloading) { selectedQuality = mode },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = mode.shortTitle,
                        color = titleCol,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mode.badge,
                        color = subCol,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = selectedQuality.footerNote,
            color = EditorialPalette.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // SECCIÓN 3: DESTINO (con líneas punteadas arriba y abajo)
        DashedDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenGallery() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GUARDAR EN",
                color = EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (selectedQuality == QualityMode.AUDIO) "Música / MultiDownloader" else "Descargas / MultiVideo",
                color = EditorialPalette.Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Abrir Galería",
                tint = EditorialPalette.Muted,
                modifier = Modifier.size(16.dp)
            )
        }
        DashedDivider()

        Spacer(modifier = Modifier.height(16.dp))

        // SECCIÓN 4: BOTÓN CTA PRINCIPAL (Ladrillo con sombra sólida #8E2C17 y divisor vertical)
        val ctaEnabled = engineReady && !isDownloading && !isUpdating
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(61.dp)
        ) {
            // Sombra inferior sólida de 3dp
            if (ctaEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .align(Alignment.BottomCenter)
                        .background(EditorialPalette.BrickDark, RoundedCornerShape(2.dp))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (ctaEnabled) EditorialPalette.Brick else EditorialPalette.DisabledBg)
                    .clickable(enabled = ctaEnabled) { triggerDownload() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isDownloading -> "Descargando…"
                        selectedQuality == QualityMode.AUDIO -> "Descargar audio"
                        else -> "Descargar video"
                    },
                    color = if (ctaEnabled) EditorialPalette.PaperInk else EditorialPalette.DisabledFg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 18.dp)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(
                            if (ctaEnabled) EditorialPalette.PaperInk.copy(alpha = 0.28f)
                            else EditorialPalette.Ink.copy(alpha = 0.14f)
                        )
                )
                Box(
                    modifier = Modifier
                        .width(57.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = if (ctaEnabled) EditorialPalette.PaperInk else EditorialPalette.DisabledFg,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(9.dp))

        // Micro-meta row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = selectedQuality.badge,
                color = EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
            Box(modifier = Modifier.width(1.dp).height(9.dp).background(EditorialPalette.LineStrong))
            Text(
                text = "AUDIO ORIGINAL",
                color = EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
            Box(modifier = Modifier.width(1.dp).height(9.dp).background(EditorialPalette.LineStrong))
            Text(
                text = "SIN CUENTA",
                color = EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
        }

        // SECCIÓN 5: SLOT DE PROGRESO (TICKET DE DESCARGA / SELLO GUARDADO / TARJETA DE ERROR)
        if (isDownloading) {
            Spacer(modifier = Modifier.height(16.dp))
            val pct = (progress * 100).toInt().coerceIn(0, 100)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorialPalette.Raised, RoundedCornerShape(2.dp))
                    .border(1.dp, EditorialPalette.Ink, RoundedCornerShape(2.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DESCARGANDO",
                        color = EditorialPalette.Brick,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "CANCELAR",
                        color = EditorialPalette.InkSoft,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.5.sp,
                        textDecoration = TextDecoration.Underline,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clickable {
                            YoutubeDL.getInstance().destroyProcessById(processId)
                            isDownloading = false
                            statusText = "Descarga cancelada por el usuario."
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$pct",
                            color = EditorialPalette.Ink,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 42.sp,
                            letterSpacing = (-2).sp
                        )
                        Text(
                            text = "%",
                            color = EditorialPalette.Ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (etaSeconds > 0) "ETA ${etaSeconds}s" else "PROCESANDO STREAM",
                            color = EditorialPalette.InkSoft,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Text(
                            text = selectedQuality.shortTitle,
                            color = EditorialPalette.Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Barra de progreso ladrillo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(EditorialPalette.Ink.copy(alpha = 0.10f), RoundedCornerShape(1.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(EditorialPalette.Brick, RoundedCornerShape(1.dp))
                    )
                }

                if (logOutput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = logOutput,
                        color = EditorialPalette.InkSoft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                DashedDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Guardando en Descargas / MultiVideo",
                        color = EditorialPalette.Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                    Text(
                        text = selectedQuality.badge,
                        color = EditorialPalette.Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (completedDownload != null) {
            val done = completedDownload!!
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorialPalette.Raised, RoundedCornerShape(2.dp))
                    .border(1.dp, EditorialPalette.Ink, RoundedCornerShape(2.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Sello verde rotado "✓ LISTO"
                    Row(
                        modifier = Modifier
                            .rotate(-3.5f)
                            .background(EditorialPalette.SuccessTint, RoundedCornerShape(2.dp))
                            .border(2.dp, EditorialPalette.Success, RoundedCornerShape(2.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EditorialPalette.Success,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "LISTO",
                            color = EditorialPalette.Success,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.6.sp
                        )
                    }

                    Text(
                        text = "ARCHIVO",
                        color = EditorialPalette.Muted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = done.title,
                    color = EditorialPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${done.sizeText} / Descargas / MultiVideo",
                    color = EditorialPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(EditorialPalette.Ink, RoundedCornerShape(2.dp))
                            .clickable {
                                if (done.uri != null) {
                                    openMediaUri(context, done.uri, done.isAudio)
                                } else {
                                    onOpenGallery()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Abrir archivo",
                            color = EditorialPalette.PaperInk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(1.dp, EditorialPalette.Ink, RoundedCornerShape(2.dp))
                            .clickable {
                                completedDownload = null
                                urlInput = ""
                                errorMessage = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Otra descarga",
                            color = EditorialPalette.Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else if (errorMessage != null && logOutput.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorialPalette.DangerSurface, RoundedCornerShape(2.dp))
                    .border(1.dp, EditorialPalette.Danger, RoundedCornerShape(2.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EditorialPalette.Danger,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "No pudimos completar la descarga",
                        color = EditorialPalette.Danger,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFF6E2A1E),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REINTENTAR",
                        color = EditorialPalette.Danger,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { triggerDownload() }
                    )
                    Text(
                        text = if (showLogs) "OCULTAR LOG ▲" else "VER DETALLES ▼",
                        color = EditorialPalette.InkSoft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        modifier = Modifier.clickable { showLogs = !showLogs }
                    )
                }

                if (showLogs) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = logOutput,
                        color = EditorialPalette.InkSoft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EditorialPalette.Raised, RoundedCornerShape(2.dp))
                            .padding(8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN 6: PESTAÑAS RECIENTES / EN COLA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            EditorialSubTab(
                title = "RECIENTES",
                count = recentMedia.size,
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 }
            )
            EditorialSubTab(
                title = "EN COLA",
                count = if (isDownloading) 1 else 0,
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 }
            )
        }
        HorizontalDivider(thickness = 1.dp, color = EditorialPalette.Line)

        if (activeSubTab == 0) {
            if (recentMedia.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EditorialPalette.LineStrong, RoundedCornerShape(2.dp))
                        .background(EditorialPalette.Raised.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Todavía no hay descargas",
                        color = EditorialPalette.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pega tu primer enlace arriba. Aquí aparecerá el archivo cuando termine.",
                        color = EditorialPalette.Muted,
                        fontSize = 11.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
                recentMedia.take(5).forEach { item ->
                    val extLabel = if (item.isVideo) "MP4" else "MP3"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openMediaUri(context, item.uri, !item.isVideo) }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (item.isVideo) EditorialPalette.Raised else EditorialPalette.SuccessTint,
                                    RoundedCornerShape(2.dp)
                                )
                                .border(
                                    1.dp,
                                    if (item.isVideo) EditorialPalette.Line else EditorialPalette.Success.copy(alpha = 0.35f),
                                    RoundedCornerShape(2.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = extLabel,
                                color = if (item.isVideo) EditorialPalette.InkSoft else EditorialPalette.Success,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.6.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = EditorialPalette.Ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.1f MB", item.sizeMb)} · ${dateFormat.format(Date(item.dateAddedMs))}",
                                color = EditorialPalette.Muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp
                            )
                        }

                        IconButton(
                            onClick = { openMediaUri(context, item.uri, !item.isVideo) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Abrir",
                                tint = EditorialPalette.InkSoft,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = EditorialPalette.Line)
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EditorialPalette.LineStrong, RoundedCornerShape(2.dp))
                    .background(EditorialPalette.Raised.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isDownloading) "1 descarga activa en curso (${(progress * 100).toInt()}%)" else "Nada en cola",
                    color = EditorialPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Los enlaces aparecen aquí mientras se descargan. De uno en uno, sin ruido.",
                    color = EditorialPalette.Muted,
                    fontSize = 11.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // SECCIÓN 7: PIE DEL MOTOR YT-DLP + BOTÓN ACTUALIZAR
        HorizontalDivider(thickness = 1.dp, color = EditorialPalette.Line)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (engineReady) EditorialPalette.Success else EditorialPalette.Brick)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MOTOR YT-DLP",
                    color = EditorialPalette.Ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = statusText,
                    color = EditorialPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .border(1.dp, EditorialPalette.LineStrong, RoundedCornerShape(2.dp))
                    .background(EditorialPalette.Raised, RoundedCornerShape(2.dp))
                    .clickable(enabled = engineReady && !isDownloading && !isUpdating) {
                        isUpdating = true
                        statusText = "Actualizando yt-dlp..."
                        scope.launch(Dispatchers.IO) {
                            try {
                                YoutubeDL.getInstance().updateYoutubeDL(
                                    context,
                                    YoutubeDL.UpdateChannel.STABLE
                                )
                                val verReq = YoutubeDLRequest("--version")
                                val verResp = YoutubeDL.getInstance().execute(verReq)
                                val newVer = verResp.out.trim()
                                withContext(Dispatchers.Main) {
                                    isUpdating = false
                                    onVersionUpdated(newVer)
                                    statusText = "Canal estable / yt-dlp $newVer"
                                }
                            } catch (e: Exception) {
                                Log.e("MultiDownloader", "Error actualizando yt-dlp", e)
                                withContext(Dispatchers.Main) {
                                    isUpdating = false
                                    statusText = "Ya está al día ($engineVersion)"
                                }
                            }
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = EditorialPalette.InkSoft,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isUpdating) "ACTUALIZANDO…" else "ACTUALIZAR",
                        color = EditorialPalette.InkSoft,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Descarga solo contenido público. Respeta los derechos de autor y las condiciones de cada plataforma.",
            color = EditorialPalette.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            lineHeight = 13.sp
        )
    }
}

@Composable
fun EditorialSubTab(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                color = if (selected) EditorialPalette.Ink else EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "$count",
                color = if (selected) EditorialPalette.Brick else EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp
            )
        }
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(2.dp)
                .background(if (selected) EditorialPalette.Ink else Color.Transparent)
        )
    }
}

@Composable
fun DashedDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = EditorialPalette.LineStrong,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}

fun openMediaUri(context: Context, uri: Uri, isAudio: Boolean) {
    try {
        val mime = if (isAudio) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("MultiDownloader", "No se pudo abrir archivo: ${e.message}")
    }
}

/**
 * Guarda el video en la Galería pública (Movies/MultiDownloader) mediante MediaStore.
 */
fun saveVideoToMediaStore(context: Context, sourceFile: File, title: String): Uri? {
    val resolver = context.contentResolver
    val cleanName = "${title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")}.mp4"

    val values = android.content.ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, cleanName)
        put(MediaStore.Video.Media.TITLE, title)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MultiDownloader")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }

    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null

    return try {
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(sourceFile.absolutePath), arrayOf("video/mp4"), null)
        uri
    } catch (e: Exception) {
        Log.e("MediaStore", "Error copiando a MediaStore", e)
        null
    }
}

/**
 * Guarda audio MP3 en la colección pública (Music/MultiDownloader) mediante MediaStore.
 */
fun saveAudioToMediaStore(context: Context, sourceFile: File, title: String): Uri? {
    val resolver = context.contentResolver
    val cleanName = "${title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")}.mp3"

    val values = android.content.ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, cleanName)
        put(MediaStore.Audio.Media.TITLE, title)
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MultiDownloader")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
    }

    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null

    return try {
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(sourceFile.absolutePath), arrayOf("audio/mpeg"), null)
        uri
    } catch (e: Exception) {
        Log.e("MediaStore", "Error copiando audio a MediaStore", e)
        null
    }
}
