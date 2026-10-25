package com.videodl

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val binStatus = MutableStateFlow<BinManager.BinStatus?>(null)
    val isRunning = MutableStateFlow(false)
    private var currentJob: kotlinx.coroutines.Job? = null

    suspend fun initBinaries() {
        BinManager.ensureBinaries(getApplication<Application>()).also { status ->
            binStatus.value = status
        }
    }

    fun runCommand(input: String) {
        isRunning.value = true
        currentJob?.cancel()
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            val args = parseInput(input)
            val status = binStatus.value ?: return@launch

            val command = mutableListOf<String>().apply {
                add(status.ytDlpPath)
                add("-v")
                add("--ffmpeg-location")
                add(status.ffmpegPath)
                add("--no-playlist")
                add("-o")
                add("%(title)s.%(ext)s")
                addAll(args)
            }

            ProcessRunner.run(command).receiveAsFlow().collectLatest { line ->
                TerminalOutput.append(line)
            }
            isRunning.value = false
        }
    }

    private fun parseInput(input: String): List<String> {
        // Si es solo URL, devolver lista con la URL
        // Si empieza con yt-dlp/ffmpeg, parsear
        return if (input.startsWith("http")) {
            listOf(input)
        } else {
            input.split(" ").filter { it.isNotBlank() }
        }
    }
}
