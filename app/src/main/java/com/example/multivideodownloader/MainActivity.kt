package com.example.multivideodownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 private var statusMessageState = mutableStateOf("Inicializando motor local yt-dlp + FFmpeg...")

 private val storagePermissionLauncher = registerForActivityResult(
 ActivityResultContracts.RequestPermission()
 ) { _ -> }

 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)

 requestLegacyStoragePermissionIfNeeded()
 handleIncomingShareIntent(intent)

 Thread {
 try {
 YoutubeDL.getInstance().init(applicationContext)
 FFmpeg.getInstance().init(applicationContext)
 Aria2c.getInstance().init(applicationContext)
 runOnUiThread {
 engineReadyState.value = true
 statusMessageState.value = "Motor listo (RedGIFs, Facebook, X, Instagram y más)."
 }
 } catch (e: YoutubeDLException) {
 Log.e("MultiDownloader", "Error inicializando librerías nativas", e)
 runOnUiThread {
 statusMessageState.value = "Error al iniciar motor: ${e.localizedMessage}"
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
 initialStatus = statusMessageState.value
 )
 }
 }
 }
 }

 override fun onNewIntent(intent: Intent) {
 super.onNewIntent(intent)
 handleIncomingShareIntent(intent)
 }

 private fun handleIncomingShareIntent(intent: Intent?) {
 if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
 intent.getStringExtra(Intent.EXTRA_TEXT)?.let { rawText ->
 val urlRegex = "(https?://\\S+)".toRegex()
 val extracted = urlRegex.find(rawText)?.value ?: rawText.trim()
 sharedUrlState.value = extracted
 }
 }
 }

 private fun requestLegacyStoragePermissionIfNeeded() {
 if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
 if (ContextCompat.checkSelfPermission(
 this,
 Manifest.permission.WRITE_EXTERNAL_STORAGE
 ) != PackageManager.PERMISSION_GRANTED
 ) {
 storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
 }
 }
 }
}

@Composable
fun DownloaderScreen(
 initialUrl: String,
 engineReady: Boolean,
 initialStatus: String
) {
 val context = LocalContext.current
 val scope = rememberCoroutineScope()

 var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
 var isDownloading by remember { mutableStateOf(false) }
 var isUpdating by remember { mutableStateOf(false) }
 var progress by remember { mutableFloatStateOf(0f) }
 var statusText by remember(initialStatus) { mutableStateOf(initialStatus) }
 var logOutput by remember { mutableStateOf("") }

 val processId = "OnDeviceBestDownload"

 Column(
 modifier = Modifier
 .fillMaxSize()
 .padding(20.dp)
 .verticalScroll(rememberScrollState()),
 verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
 Text(
 text = "MultiVideo Downloader",
 style = MaterialTheme.typography.headlineMedium,
 fontWeight = FontWeight.Bold
 )

 Text(
 text = "Descarga directa en el dispositivo en máxima calidad (RedGIFs, Facebook, X, Instagram)",
 style = MaterialTheme.typography.bodyMedium,
 color = MaterialTheme.colorScheme.onSurfaceVariant
 )

 OutlinedTextField(
 value = urlInput,
 onValueChange = { urlInput = it },
 label = { Text("Enlace público del video") },
 placeholder = { Text("https://...") },
 modifier = Modifier.fillMaxWidth(),
 singleLine = true
 )

 Row(
 modifier = Modifier.fillMaxWidth(),
 horizontalArrangement = Arrangement.spacedBy(12.dp)
 ) {
 OutlinedButton(
 onClick = {
 val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
 val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
 if (!pasted.isNullOrBlank()) {
 urlInput = pasted.trim()
 }
 },
 modifier = Modifier.weight(1f),
 enabled = !isDownloading
 ) {
 Text("Pegar enlace")
 }

 Button(
 onClick = {
 val targetUrl = urlInput.trim()
 if (targetUrl.isEmpty()) {
 statusText = "Por favor, ingresa o pega un enlace válido."
 return@Button
 }
 isDownloading = true
 progress = 0f
 statusText = "Analizando enlace y buscando la máxima calidad..."
 logOutput = ""

 scope.launch(Dispatchers.IO) {
 try {
 val downloadDir = File(
 Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
 "MultiDownloader"
 )
 if (!downloadDir.exists()) {
 downloadDir.mkdirs()
 }

 val request = YoutubeDLRequest(targetUrl).apply {
 addOption("--no-mtime")
 addOption("--no-playlist")
 addOption(
 "-f",
 "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best"
 )
 addOption("--merge-output-format", "mp4")
 addOption(
 "-o",
 downloadDir.absolutePath + "/%(extractor)s_%(id)s.%(ext)s"
 )
 }

 YoutubeDL.getInstance().execute(
 request,
 processId
 ) { currentProgress, etaInSeconds, line ->
 scope.launch(Dispatchers.Main) {
 if (currentProgress >= 0f) {
 progress = (currentProgress / 100f).coerceIn(0f, 1f)
 }
 statusText = "Descargando: ${currentProgress.toInt()}% (ETA: ${etaInSeconds}s)"
 if (line.isNotBlank()) {
 logOutput = line
 }
 }
 }

 val files = downloadDir.listFiles()?.map { it.absolutePath }?.toTypedArray()
 if (!files.isNullOrEmpty()) {
 MediaScannerConnection.scanFile(context, files, null, null)
 }

 withContext(Dispatchers.Main) {
 isDownloading = false
 progress = 1f
 statusText = "¡Descarga completada! Guardado en Descargas/MultiDownloader"
 }
 } catch (e: Exception) {
 withContext(Dispatchers.Main) {
 isDownloading = false
 statusText = "Error en la descarga: ${e.localizedMessage}"
 }
 }
 }
 },
 modifier = Modifier.weight(1f),
 enabled = engineReady && !isDownloading && !isUpdating
 ) {
 Text(if (isDownloading) "Descargando..." else "Descargar MP4")
 }
 }

 if (isDownloading) {
 LinearProgressIndicator(
 progress = { progress },
 modifier = Modifier.fillMaxWidth(),
 )
 OutlinedButton(
 onClick = {
 YoutubeDL.getInstance().destroyProcessById(processId)
 isDownloading = false
 statusText = "Descarga cancelada por el usuario."
 },
 modifier = Modifier.align(Alignment.End)
 ) {
 Text("Cancelar")
 }
 }

 Card(
 modifier = Modifier.fillMaxWidth()
 ) {
 Column(
 modifier = Modifier.padding(16.dp),
 verticalArrangement = Arrangement.spacedBy(8.dp)
 ) {
 Text(
 text = "Estado",
 style = MaterialTheme.typography.titleSmall,
 fontWeight = FontWeight.SemiBold
 )
 Text(
 text = statusText,
 style = MaterialTheme.typography.bodyMedium,
 )
 if (logOutput.isNotBlank()) {
 Text(
 text = logOutput,
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant
 )
 }
 }
 }

 HorizontalDivider()

 OutlinedButton(
 onClick = {
 isUpdating = true
 statusText = "Buscando actualizaciones de extractores yt-dlp..."
 scope.launch(Dispatchers.IO) {
 try {
 val status = YoutubeDL.getInstance().updateYoutubeDL(
 context,
 YoutubeDL.UpdateChannel.STABLE
 )
 withContext(Dispatchers.Main) {
 isUpdating = false
 statusText = "Estado de actualización de yt-dlp: $status"
 }
 } catch (e: Exception) {
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
 Text(if (isUpdating) "Actualizando extractores..." else "Actualizar extractores (yt-dlp)")
 }
 }
}
