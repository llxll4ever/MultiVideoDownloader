package com.videodl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object ProcessRunner {
    /** Ejecuta comando y emite cada línea de stdout/stderr combinado */
    fun run(command: List<String>): Channel<String> {
        val outputChannel = Channel<String>(Channel.BUFFERED)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pb = ProcessBuilder(command)
                pb.redirectErrorStream(true)
                val process = pb.start()

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { outputChannel.send(it) }
                    }
                }
                process.waitFor()
                outputChannel.send("__EXIT_CODE__${process.exitValue()}")
            } catch (e: Exception) {
                outputChannel.send("__ERROR__${e.message}")
            } finally {
                outputChannel.close()
            }
        }
        return outputChannel
    }
    
    /** Versión suspendida que recolecta todo */
    suspend fun runCollect(command: List<String>): Result = withContext(Dispatchers.IO) {
        val output = mutableListOf<String>()
        var exitCode = -1
        var error: String? = null
        
        try {
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(true)
            val process = pb.start()
            
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { output.add(it) }
                }
            }
            exitCode = process.waitFor()
        } catch (e: Exception) {
            error = e.message
        }
        Result(output, exitCode, error)
    }
    
    data class Result(
        val lines: List<String>,
        val exitCode: Int,
        val error: String?
    )
}
