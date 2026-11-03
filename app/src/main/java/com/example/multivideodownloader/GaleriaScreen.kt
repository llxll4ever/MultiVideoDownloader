package com.example.multivideodownloader

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    LaunchedEffect(Unit) {
        mediaList = queryDownloadedMedia(context)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialPalette.Paper)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // Eyebrow
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ARCHIVO LOCAL",
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

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Galería.",
                color = EditorialPalette.Ink,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Text(
                text = "${mediaList.size} ARCHIVOS",
                color = EditorialPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EditorialPalette.Brick)
            }
        } else if (mediaList.isEmpty()) {
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
                        text = "Sin archivos guardados",
                        color = EditorialPalette.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tus videos y audios descargados aparecerán aquí.",
                        color = EditorialPalette.Muted,
                        fontSize = 12.sp
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
                items(mediaList, key = { "${it.isVideo}_${it.id}" }) { item ->
                    MediaCard(context = context, item = item) { deletedItem ->
                        mediaList = mediaList.filterNot { it.id == deletedItem.id && it.isVideo == deletedItem.isVideo }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCard(context: Context, item: MediaItem, onDelete: (MediaItem) -> Unit) {
    Column(
        modifier = Modifier
            .height(206.dp)
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
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Sello MP4 / MP3 en esquina superior izquierda
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
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.8.sp
                )
            }

            // Botón central Play
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = EditorialPalette.Ink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${String.format(Locale.US, "%.1f MB", item.sizeMb)} · ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.dateAddedMs))}",
                    color = EditorialPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    try {
                        val deleted = context.contentResolver.delete(item.uri, null, null)
                        if (deleted > 0) onDelete(item)
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar",
                    tint = EditorialPalette.Danger,
                    modifier = Modifier.size(17.dp)
                )
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
