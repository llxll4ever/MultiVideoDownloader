package com.videodl

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels(
        factoryProducer = { ViewModelProvider.AndroidViewModelFactory(application) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestPermissions()
        setContent {
            VideoDownloaderTheme {
                TerminalView(
                    onRunCommand = { input ->
                        viewModel.runCommand(input)
                    },
                    binStatus = viewModel.binStatus.value,
                    isRunning = viewModel.isRunning.value
                )
            }
        }

        // Inicializar binarios al arrancar
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.initBinaries()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "downloads", "Descargas", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: permisos de medios granulares, no hace falta MANAGE_EXTERNAL_STORAGE
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_VIDEO), 100)
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
    }
}
