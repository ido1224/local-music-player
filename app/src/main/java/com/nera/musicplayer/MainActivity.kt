package com.nera.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nera.musicplayer.ui.NeraApp
import com.nera.musicplayer.ui.theme.NeraMusicPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeraMusicPlayerTheme {
                NeraApp()
            }
        }
    }
}
