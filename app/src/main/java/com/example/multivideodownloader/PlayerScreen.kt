package com.example.multivideodownloader

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity

class PlayerScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUriString = intent?.dataString
        if (videoUriString.isNullOrBlank()) {
            finish()
            return
        }
        val videoUri = Uri.parse(videoUriString)

        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        val videoView = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            setVideoURI(videoUri)
            val mc = MediaController(this@PlayerScreen)
            mc.setAnchorView(this)
            setMediaController(mc)
            keepScreenOn = true
            requestFocus()
            start()
        }
        setContentView(videoView)
    }
}
