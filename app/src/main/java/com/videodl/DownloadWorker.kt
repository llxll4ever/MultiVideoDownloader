package com.videodl

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_ARGS = "extra_args"
        const val TAG = "DownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(EXTRA_URL) ?: return@withContext Result.failure()
        val extraArgs = inputData.getString(EXTRA_ARGS)?.split(" ") ?: emptyList()

        // Asegurar binarios
        val binStatus = BinManager.ensureBinaries(applicationContext)

        // Construir comando yt-dlp
        val command = mutableListOf<String>().apply {
            add(binStatus.ytDlpPath)
            add("-v") // verbose para logs
            add("--ffmpeg-location")
            add(binStatus.ffmpegPath)
            add("--no-playlist")
            add("-o")
            add("%(title)s.%(ext)s")
            addAll(extraArgs)
            add(url)
        }

        Log.i(TAG, "Ejecutando: ${command.joinToString(" ")}")

        val result = ProcessRunner.runCollect(command)

        // Log completo a logcat
        result.lines.forEach { Log.i(TAG, it) }

        return@withContext if (result.exitCode == 0) {
            Result.success()
        } else {
            Result.retry() // Reintenta con backoff exponencial
        }
    }
}
