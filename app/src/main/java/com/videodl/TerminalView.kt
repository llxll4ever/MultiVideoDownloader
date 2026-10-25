package com.videodl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------------------------------------------------------------------------
// Paleta terminal estilo VS Code
// ---------------------------------------------------------------------------
private val TerminalBg = Color(0xFF1E1E1E)
private val TerminalSurface = Color(0xFF2D2D2D)
private val ColorDownload = Color(0xFF4EC9B0)
private val ColorFfmpeg = Color(0xFFDCDCAA)
private val ColorDebug = Color(0xFF9CDCFE)
private val ColorInfo = Color(0xFFB5CEA8)
private val ColorError = Color(0xFFF44747)
private val ColorExit = Color(0xFFCE9178)
private val ColorText = Color(0xFFD4D4D4)
private val ColorHint = Color(0xFF6A9955)

/**
 * Bus de salida del proceso. MainViewModel.runCommand() debe llamar a
 * [append] por cada línea que emita ProcessRunner.run(), y TerminalView
 * recolecta [lines] con collectAsState() para pintarlas en el terminal.
 */
object TerminalOutput {
    private const val MAX_LINES = 2000

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun append(line: String) {
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}

/** Estilo de color por prefijo de línea (yt-dlp / ffmpeg). */
private fun terminalLineStyle(line: String): TextStyle {
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    return when {
        line.startsWith("[download]") -> mono.copy(color = ColorDownload)
        line.startsWith("[ffmpeg]") -> mono.copy(color = ColorFfmpeg)
        line.startsWith("[debug]") -> mono.copy(color = ColorDebug)
        line.startsWith("[info]") -> mono.copy(color = ColorInfo)
        line.startsWith("__EXIT_CODE__") -> mono.copy(color = ColorExit, fontWeight = FontWeight.Bold)
        line.startsWith("__ERROR__") -> mono.copy(color = ColorError, fontWeight = FontWeight.Bold)
        line.startsWith("[error]") -> mono.copy(color = ColorError)
        else -> mono.copy(color = ColorText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalView(
    onRunCommand: (String) -> Unit,
    binStatus: BinManager.BinStatus?,
    isRunning: Boolean
) {
    val lines by TerminalOutput.lines.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showManualCmd by remember { mutableStateOf(false) }

    // Scroll automático al final cuando llegan líneas nuevas
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Scaffold(
        containerColor = TerminalBg,
        topBar = {
            TopAppBar(
                title = { Text("VideoDownloader", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalBg,
                    titleContentColor = ColorText
                ),
                actions = {
                    IconButton(onClick = { showManualCmd = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Card de status: versiones yt-dlp / ffmpeg
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = TerminalSurface)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "yt-dlp: ${binStatus?.ytDlpVersion ?: "inicializando..."}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ColorDownload
                        )
                    )
                    Text(
                        "ffmpeg: ${binStatus?.ffmpegVersion?.take(30) ?: "inicializando..."}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ColorFfmpeg
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isRunning) {
                        Text(
                            "⏳ EJECUTANDO...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ColorExit,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Salida del terminal
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        style = terminalLineStyle(line),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }

            // Línea de entrada: "$" + input + botón descargar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning,
                    singleLine = true,
                    label = {
                        Text(
                            "$",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = ColorDownload
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            "ej: URL (redgifs, vimeo, youtube, etc.)",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = ColorHint
                            )
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                onRunCommand(inputText)
                                inputText = ""
                            }
                        }
                    )
                )
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onRunCommand(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !isRunning && inputText.isNotBlank()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Descargar")
                }
            }
        }
    }

    if (showManualCmd) {
        ManualCommandDialog(
            onDismiss = { showManualCmd = false },
            onRun = { cmd ->
                onRunCommand(cmd)
                showManualCmd = false
            }
        )
    }
}

@Composable
fun ManualCommandDialog(onDismiss: () -> Unit, onRun: (String) -> Unit) {
    var cmd by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = TerminalSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Comando manual (yt-dlp / ffmpeg)",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorText
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cmd,
                    onValueChange = { cmd = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "ej: -v URL o -i in.mp4 -c copy out.mp4",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = ColorHint
                            )
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = ColorText
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (cmd.isNotBlank()) onRun(cmd) }
                    )
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (cmd.isNotBlank()) onRun(cmd) },
                        enabled = cmd.isNotBlank()
                    ) {
                        Text("Ejecutar")
                    }
                }
            }
        }
    }
}
