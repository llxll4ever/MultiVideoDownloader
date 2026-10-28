package com.example.multivideodownloader

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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

@Composable
fun GaleriaScreen() {
    val context = LocalContext.current
    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Buscando archivos en Galería...") }

    LaunchedEffect(Unit) {
        mediaList = queryDownloadedMedia(context)
        isLoading = false
        message = if (mediaList.isEmpty()) "No se encontraron descargas en la Galería." else "Descargas en Galería (${mediaList.size})"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "Galería de Descargas",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mediaList) { item ->
                    MediaCard(context = context, item = item) { deletedItem ->
                        mediaList = mediaList.filter { it.id != deletedItem.id }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCard(context: Context, item: MediaItem, onDelete: (MediaItem) -> Unit) {
    Card(
        modifier = Modifier
            .height(200.dp)
            .clickable {
                // Open with default video player
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, item.uri).apply {
                    setDataAndType(item.uri, "video/*")
                }
                context.startActivity(intent)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thumbnail via ContentResolver thumbnail API (API 29+ available, graceful fallback)
            Box(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Play icon overlay
                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
            // Footer with info and delete
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${item.sizeMb.toInt()} MB · ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.dateAddedMs))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    val deleted = context.contentResolver.delete(item.uri, null, null)
                    if (deleted > 0) onDelete(item)
                }) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
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
    // Audio
    val audioSelection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
    val audioArgs = arrayOf("Music/MultiDownloader/%")
    val audioProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.RELATIVE_PATH
    )
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
    return result.sortedByDescending { it.dateAddedMs }
}
