package com.videodl

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.withContext

object BinManager {
    private const val TAG = "BinManager"
    private const val BIN_DIR = "bin"
    private const val YT_DLP_ASSET = "yt-dlp_linux_arm64"
    private const val FFMPEG_ASSET = "ffmpeg_arm64"
    private const val VERSION_FILE = "bin_version.txt"
    
    data class BinStatus(
        val ytDlpPath: String,
        val ffmpegPath: String,
        val ytDlpVersion: String,
        val ffmpegVersion: String
    )

    suspend fun ensureBinaries(context: Context): BinStatus = withContext(ioDispatcher) {
        val filesDir = context.filesDir
        val binDir = File(filesDir, BIN_DIR)
        binDir.mkdirs()
        
        val ytDlpBin = File(binDir, YT_DLP_ASSET)
        val ffmpegBin = File(binDir, FFMPEG_ASSET)
        val versionFile = File(binDir, VERSION_FILE)
        
        // Copiar si no existen o version cambió
        copyIfNeeded(context, YT_DLP_ASSET, ytDlpBin)
        copyIfNeeded(context, FFMPEG_ASSET, ffmpegBin)
        
        // chmod +x
        ytDlpBin.setExecutable(true)
        ffmpegBin.setExecutable(true)
        
        // Leer versiones
        val ytDlpVer = getVersion(ytDlpBin, "--version")
        val ffmpegVer = getVersion(ffmpegBin, "-version").takeIf { it.isNotBlank() }?.lines()?.firstOrNull() ?: "unknown"
        
        // Guardar version actual
        versionFile.writeText("$ytDlpVer|$ffmpegVer")
        
        BinStatus(
            ytDlpPath = ytDlpBin.absolutePath,
            ffmpegPath = ffmpegBin.absolutePath,
            ytDlpVersion = ytDlpVer,
            ffmpegVersion = ffmpegVer
        )
    }

    private suspend fun copyIfNeeded(context: Context, assetName: String, dest: File) = withContext(ioDispatcher) {
        if (dest.exists()) return@withContext
        context.assets.open(assetName).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        Log.i(TAG, "Copiado $assetName a ${dest.absolutePath}")
    }

    private fun getVersion(bin: File, arg: String): String {
        return try {
            ProcessBuilder(bin.absolutePath, arg)
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader()
                .use { it.readText().trim() }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private val ioDispatcher = kotlinx.coroutines.Dispatchers.IO
}
