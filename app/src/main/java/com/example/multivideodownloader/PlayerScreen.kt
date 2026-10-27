package com.example.multivideodownloader

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity

class PlayerScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUriString = intent?.dataString ?: return finish()
        val videoUri = Uri.parse(videoUriString)

        val videoView = VideoView(this).apply {
            setVideoURI(videoUri)
            val mc = MediaController(this@PlayerScreen)
            mc.setAnchorView(this)
            setMediaController(mc)
            requestFocus()
            start()
        }
        setContentView(videoView)
    }
}
