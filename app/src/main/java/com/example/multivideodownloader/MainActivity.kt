package com.example.multivideodownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var sharedUrlState = mutableStateOf("")
    private var engineReadyState = mutableStateOf(false)
    private var engineVersionState = mutableStateOf("cargando...")
    private var statusMessageState = mutableStateOf("Inicializando motor yt-dlp + FFmpeg...")

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

                val version = getInstalledYtDlpVersion()

                runOnUiThread {
                    engineReadyState.value = true
                    engineVersionState.value = version
                    statusMessageState.value = "Motor listo (yt-dlp $version)."
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DownloaderScreen(
                        initialUrl = sharedUrlState.value,
                        engineReady = engineReadyState.value,
                        engineVersion = engineVersionState.value,
                        initialStatus = statusMessageState.value,
                        onVersionUpdated = { newVer ->
                            engineVersionState.value = newVer
                        }
                    )
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
            }
        }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }
}

fun cleanIncomingUrl(raw: String): String {
    val regex = "(https?://[^\\s]+)".toRegex()
    val match = regex.find(raw)?.value ?: raw.trim()
    // Normalizar enlaces comunes y limpiar tracking parameters redundantes si es seguro
    return match.trimEnd('/', ' ', '\n', '\r')
}

enum class QualityMode(val label: String, val subtitle: String) {
    BEST("Máxima (1080p+)", "Mejor calidad disponible"),
    HD720("720p", "Descarga rápida y ligera"),
    AUDIO("Solo Audio", "MP3 alta fidelidad")
}

@Composable
fun DownloaderScreen(
    initialUrl: String,
    engineReady: Boolean,
    engineVersion: String,
    initialStatus: String,
    onVersionUpdated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var selectedQuality by remember { mutableStateOf(QualityMode.BEST) }
    var isDownloading by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember(initialStatus) { mutableStateOf(initialStatus) }
    var logOutput by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }

    val processId = remember { "MultiVideoDownload_${System.currentTimeMillis()}" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado con estado del motor
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MultiVideo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Downloader on-device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Badge del estado de yt-dlp
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (engineReady) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (engineReady) Color(0xFF4CAF50) else Color(0xFFE53935))
                    )
                    Text(
                        text = if (engineReady) "yt-dlp v$engineVersion" else "Iniciando...",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Campo de entrada de URL
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Enlace del video (X, TikTok, RedGIFs, IG, FB...)") },
            placeholder = { Text("https://x.com/... o https://...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (urlInput.isNotEmpty() && !isDownloading) {
                    IconButton(onClick = { urlInput = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            }
        )

        // Botones de pegar y limpiar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!pasted.isNullOrBlank()) {
                        urlInput = cleanIncomingUrl(pasted)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isDownloading
            ) {
                Text("Pegar enlace")
            }

            if (urlInput.isNotBlank()) {
                OutlinedButton(
                    onClick = { urlInput = "" },
                    enabled = !isDownloading
                ) {
                    Text("Borrar")
                }
            }
        }

        // Selector de Calidad (Segmented-like)
        Text(
            text = "Calidad de descarga",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QualityMode.values().forEach { mode ->
                val isSelected = selectedQuality == mode
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) ButtonDefaults.outlinedButtonBorder else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isDownloading) { selectedQuality = mode }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Información de destino
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📁 Destino:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedQuality == QualityMode.AUDIO) "Música / MultiDownloader (Galería & Archivos)" else "Películas / MultiDownloader (Galería & Fotos)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Botón principal de Descarga
        Button(
            onClick = {
                val targetUrl = cleanIncomingUrl(urlInput.trim())
                if (targetUrl.isEmpty()) {
                    statusText = "Por favor ingresa o pega un enlace válido."
                    return@Button
                }

                isDownloading = true
                progress = 0f
                statusText = "Analizando enlace y buscando streams..."
                logOutput = ""

                scope.launch(Dispatchers.IO) {
                    try {
                        // Directorio de staging privado para evitar errores de permisos Scoped Storage
                        val stagingDir = File(
                            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
                            "staging"
                        )
                        if (!stagingDir.exists()) {
                            stagingDir.mkdirs()
                        }

                        // Limpiar archivos temporales previos
                        stagingDir.listFiles()?.forEach { it.delete() }

                        val isAudioOnly = selectedQuality == QualityMode.AUDIO

                        val request = YoutubeDLRequest(targetUrl).apply {
                            addOption("--no-mtime")
                            addOption("--no-playlist")
                            addOption("--no-check-certificates")
                            addOption("--prefer-free-formats")
                            addOption("--retries", "3")
                            addOption("--fragment-retries", "5")
                            addOption("--concurrent-fragments", "4")

                            when (selectedQuality) {
                                QualityMode.BEST -> {
                                    // Formato resiliente: combina video y audio si existen, o toma el mejor combinado
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

                            // Nombre seguro acotado
                            addOption("-o", "${stagingDir.absolutePath}/%(title).90s_%(id)s.%(ext)s")
                        }

                        var lastLine = ""
                        YoutubeDL.getInstance().execute(
                            request,
                            processId
                        ) { currentProgress, etaInSeconds, line ->
                            scope.launch(Dispatchers.Main) {
                                if (currentProgress >= 0f) {
                                    progress = (currentProgress / 100f).coerceIn(0f, 1f)
                                }
                                if (etaInSeconds > 0) {
                                    statusText = "Descargando: ${currentProgress.toInt()}% (ETA: ${etaInSeconds}s)"
                                } else {
                                    statusText = "Descargando: ${currentProgress.toInt()}%"
                                }
                                if (line.isNotBlank()) {
                                    lastLine = line
                                    logOutput = line
                                }
                            }
                        }

                        // Localizar archivo resultante en staging
                        val downloadedFiles = stagingDir.listFiles { file ->
                            file.isFile && file.length() > 0 && !file.name.endsWith(".part") && !file.name.endsWith(".ytdl")
                        }

                        if (!downloadedFiles.isNullOrEmpty()) {
                            val savedFile = downloadedFiles.first()
                            val cleanTitle = savedFile.nameWithoutExtension

                            // Guardar en MediaStore (Galería / Álbum de fotos)
                            val publicUri = if (isAudioOnly) {
                                saveAudioToMediaStore(context, savedFile, cleanTitle)
                            } else {
                                saveVideoToMediaStore(context, savedFile, cleanTitle)
                            }

                            withContext(Dispatchers.Main) {
                                isDownloading = false
                                progress = 1f
                                statusText = if (publicUri != null) {
                                    "¡Descarga exitosa! Guardado en tu Galería (${savedFile.name})"
                                } else {
                                    "Descarga completada en almacenamiento local (${savedFile.name})"
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isDownloading = false
                                statusText = "El proceso finalizó pero no se generó el archivo. Revisa los logs abajo."
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MultiDownloader", "Error en descarga", e)
                        val err = e.localizedMessage ?: e.message ?: "Error desconocido"
                        val isOutdated = err.contains("outdated", ignoreCase = true) ||
                                err.contains("No video could be found", ignoreCase = true) ||
                                err.contains("extractor", ignoreCase = true)

                        withContext(Dispatchers.Main) {
                            isDownloading = false
                            if (isOutdated) {
                                statusText = "El extractor falló o está desactualizado para esta plataforma. Usa el botón 'Actualizar extractores' abajo para descargar la última versión de yt-dlp."
                            } else {
                                statusText = "Error: $err"
                            }
                            logOutput = err
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = engineReady && !isDownloading && !isUpdating
        ) {
            Text(
                text = if (isDownloading) "Descargando..." else "Descargar a la Galería",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Progreso activo y botón Cancelar
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            OutlinedButton(
                onClick = {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                    isDownloading = false
                    statusText = "Descarga cancelada por el usuario."
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Cancelar descarga")
            }
        }

        // Tarjeta de Estado
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Estado",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (logOutput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (showLogs) "Ocultar detalles ▲" else "Ver detalles del extractor ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showLogs = !showLogs }
                    )
                    if (showLogs) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                text = logOutput,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Divider()

        // Botón de Actualizar Extractores (yt-dlp)
        OutlinedButton(
            onClick = {
                isUpdating = true
                statusText = "Buscando y descargando última versión de yt-dlp desde GitHub..."
                scope.launch(Dispatchers.IO) {
                    try {
                        val status = YoutubeDL.getInstance().updateYoutubeDL(
                            context,
                            YoutubeDL.UpdateChannel.STABLE
                        )
                        // Consultar nueva versión
                        val verReq = YoutubeDLRequest("--version")
                        val verResp = YoutubeDL.getInstance().execute(verReq)
                        val newVer = verResp.out.trim()

                        withContext(Dispatchers.Main) {
                            isUpdating = false
                            onVersionUpdated(newVer)
                            statusText = "¡Motor actualizado a versión $newVer! Resultado: $status"
                        }
                    } catch (e: Exception) {
                        Log.e("MultiDownloader", "Error actualizando yt-dlp", e)
                        withContext(Dispatchers.Main) {
                            isUpdating = false
                            statusText = "No se pudo actualizar: ${e.localizedMessage}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = engineReady && !isDownloading && !isUpdating
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isUpdating) "Actualizando motor..." else "Actualizar motor (yt-dlp)",
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Guarda el video en la Galería pública (Movies/MultiDownloader) mediante MediaStore.
 * Funciona de forma segura en Android 10, 11, 12, 13, 14 y 15 sin errores de Scoped Storage.
 */
fun saveVideoToMediaStore(context: Context, sourceFile: File, title: String): Uri? {
    val resolver = context.contentResolver
    val cleanName = "${title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")}.mp4"

    val values = ContentValues().apply {
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
        MediaScannerConnection.scanFile(context, arrayOf(sourceFile.absolutePath), arrayOf("video/mp4"), null)
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

    val values = ContentValues().apply {
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
        MediaScannerConnection.scanFile(context, arrayOf(sourceFile.absolutePath), arrayOf("audio/mpeg"), null)
        uri
    } catch (e: Exception) {
        Log.e("MediaStore", "Error copiando audio a MediaStore", e)
        null
    }
}
