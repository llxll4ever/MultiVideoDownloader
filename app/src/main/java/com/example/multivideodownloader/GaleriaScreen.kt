package com.example.multivideodownloader

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeMb: Double,
    val dateAddedMs: Long,
    val isVideo: Boolean
)

enum class GalleryFilter(val label: String) {
    ALL("TODOS"),
    VIDEO("MP4"),
    AUDIO("MP3")
}

@Composable
fun GaleriaScreen(refreshTick: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var manualRefreshTick by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf(GalleryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }

    val videoImageLoader = remember(context) {
        ImageLoader.Builder(context.applicationContext)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        val target = pendingDeleteItem
        pendingDeleteItem = null
        if (res.resultCode == Activity.RESULT_OK && target != null) {
            mediaList = mediaList.filterNot { it.id == target.id && it.isVideo == target.isVideo }
            DownloadController.notifyMediaChanged()
        }
    }

    fun requestDeleteItem(item: MediaItem) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val deleted = context.contentResolver.delete(item.uri, null, null)
                    if (deleted > 0) return@withContext "deleted"
                } catch (secEx: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && secEx is RecoverableSecurityException) {
                        return@withContext secEx
                    }
                } catch (_: Exception) {
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return@withContext runCatching {
                        MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri)).intentSender
                    }.getOrNull() ?: "error"
                }
                "error"
            }

            when {
                outcome == "deleted" -> {
                    mediaList = mediaList.filterNot { it.id == item.id && it.isVideo == item.isVideo }
                    DownloadController.notifyMediaChanged()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outcome is RecoverableSecurityException -> {
                    pendingDeleteItem = item
                    deleteLauncher.launch(IntentSenderRequest.Builder(outcome.userAction.actionIntent.intentSender).build())
                }
                outcome is android.content.IntentSender -> {
                    pendingDeleteItem = item
                    deleteLauncher.launch(IntentSenderRequest.Builder(outcome).build())
                }
                else -> {
                    Toast.makeText(context, "No se pudo eliminar el archivo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(refreshTick, manualRefreshTick) {
        if (mediaList.isEmpty()) isLoading = true
        mediaList = withContext(Dispatchers.IO) { queryDownloadedMedia(context) }
        isLoading = false
    }

    val filteredList = remember(mediaList, selectedFilter, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        mediaList.filter { item ->
            val matchType = when (selectedFilter) {
                GalleryFilter.ALL -> true
                GalleryFilter.VIDEO -> item.isVideo
                GalleryFilter.AUDIO -> !item.isVideo
            }
            val matchQuery = q.isEmpty() || item.name.lowercase(Locale.ROOT).contains(q)
            matchType && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialPalette.Paper)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "BÓVEDA LOCAL // EXPEDIENTES",
                color = EditorialPalette.Brick,
                fontFamily = EditorialType.MonoTypewriter,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 1.dp,
                color = EditorialPalette.LineStrong
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Archivo.",
                    color = EditorialPalette.Ink,
                    fontFamily = EditorialType.DisplayTypewriter,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Movies / MultiDownloader · Music",
                    color = EditorialPalette.Muted,
                    fontFamily = EditorialType.MonoTypewriter,
                    fontSize = 10.sp
                )
            }

            TopSecretStamp(
                spec = StampSpec(
                    topSerial = "REGISTRO // LOCAL",
                    mainStamp = "★ ${mediaList.size} ITEMS ★",
                    bottomCode = "ACCESO AUTORIZADO",
                    inkColor = EditorialPalette.Ink,
                    tiltDegrees = -3.2f
                ),
                compact = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filtros TODOS / MP4 / MP3 + Refrescar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GalleryFilter.values().forEach { filter ->
                    val active = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) EditorialPalette.Ink else EditorialPalette.Raised)
                            .border(
                                1.dp,
                                if (active) EditorialPalette.Ink else EditorialPalette.LineStrong,
                                RoundedCornerShape(2.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            color = if (active) EditorialPalette.PaperInk else EditorialPalette.InkSoft,
                            fontFamily = EditorialType.MonoTypewriter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EditorialPalette.Raised)
                    .border(1.dp, EditorialPalette.LineStrong, RoundedCornerShape(2.dp))
                    .clickable { manualRefreshTick += 1 }
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refrescar galería",
                        tint = EditorialPalette.InkSoft,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "RECARGAR",
                        color = EditorialPalette.InkSoft,
                        fontFamily = EditorialType.MonoTypewriter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }

        if (mediaList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(EditorialPalette.Raised, RoundedCornerShape(2.dp))
                    .border(1.dp, EditorialPalette.LineStrong, RoundedCornerShape(2.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = EditorialPalette.Muted,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar expediente...",
                            color = EditorialPalette.Muted.copy(alpha = 0.75f),
                            fontFamily = EditorialType.MonoTypewriter,
                            fontSize = 11.5.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = EditorialPalette.Ink,
                            fontFamily = EditorialType.MonoTypewriter,
                            fontSize = 11.5.sp
                        ),
                        cursorBrush = SolidColor(EditorialPalette.Brick),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar búsqueda",
                            tint = EditorialPalette.Muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EditorialPalette.Brick)
            }
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EditorialPalette.LineStrong, RoundedCornerShape(2.dp))
                    .background(EditorialPalette.Raised.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (mediaList.isEmpty()) "Sin expedientes guardados" else "Sin coincidencias",
                        color = EditorialPalette.Ink,
                        fontFamily = EditorialType.DisplayTypewriter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (mediaList.isEmpty()) {
                            "Tus videos y audios descargados aparecerán aquí."
                        } else {
                            "Cambia el filtro o borra el texto de búsqueda."
                        },
                        color = EditorialPalette.Muted,
                        fontFamily = EditorialType.MonoTypewriter,
                        fontSize = 11.5.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredList, key = { "${it.isVideo}_${it.id}" }) { item ->
                    MediaCard(
                        context = context,
                        item = item,
                        videoImageLoader = videoImageLoader,
                        onDelete = { requestDeleteItem(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun MediaCard(
    context: Context,
    item: MediaItem,
    videoImageLoader: ImageLoader,
    onDelete: (MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .height(212.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(EditorialPalette.Raised)
            .border(1.dp, EditorialPalette.LineStrong, RoundedCornerShape(2.dp))
            .clickable {
                openMediaUri(context, item.uri, !item.isVideo)
            }
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(EditorialPalette.Ink.copy(alpha = 0.08f))
        ) {
            if (item.isVideo) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .videoFrameMillis(1000L)
                        .crossfade(true)
                        .build(),
                    imageLoader = videoImageLoader,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(
                        if (item.isVideo) EditorialPalette.Ink else EditorialPalette.Success,
                        RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (item.isVideo) "MP4" else "MP3",
                    color = EditorialPalette.PaperInk,
                    fontFamily = EditorialType.DisplayTypewriter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.Center)
                    .background(EditorialPalette.Ink.copy(alpha = 0.78f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isVideo) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                    contentDescription = "Reproducir",
                    tint = EditorialPalette.PaperInk,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = EditorialPalette.Line)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = item.name,
                color = EditorialPalette.Ink,
                fontFamily = EditorialType.MonoTypewriter,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1fMB", item.sizeMb)} · ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.dateAddedMs))}",
                    color = EditorialPalette.Muted,
                    fontFamily = EditorialType.MonoTypewriter,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(
                        onClick = { shareMediaUri(context, item.uri, !item.isVideo) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir",
                            tint = EditorialPalette.Brick,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDelete(item) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Eliminar",
                            tint = EditorialPalette.Danger,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

fun queryDownloadedMedia(context: Context): List<MediaItem> {
    val result = mutableListOf<MediaItem>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.RELATIVE_PATH
    )
    val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
    val args = arrayOf("Movies/MultiDownloader/%")
    val resolver = context.contentResolver
    try {
        val videoQuery = resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, selection, args, null
        )
        videoQuery?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "video.mp4"
                val sizeBytes = cursor.getLong(sizeCol)
                val dateMs = cursor.getLong(dateCol) * 1000L
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                result.add(MediaItem(id, contentUri, name, sizeBytes / 1e6, dateMs, isVideo = true))
            }
        }
    } catch (_: Exception) {
    }

    val audioSelection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
    val audioArgs = arrayOf("Music/MultiDownloader/%")
    val audioProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.RELATIVE_PATH
    )
    try {
        val audioQuery = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioProjection, audioSelection, audioArgs, null)
        audioQuery?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "audio.mp3"
                val sizeBytes = cursor.getLong(sizeCol)
                val dateMs = cursor.getLong(dateCol) * 1000L
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                result.add(MediaItem(id, contentUri, name, sizeBytes / 1e6, dateMs, isVideo = false))
            }
        }
    } catch (_: Exception) {
    }
    return result.sortedByDescending { it.dateAddedMs }
}
