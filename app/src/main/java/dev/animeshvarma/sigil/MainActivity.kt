package dev.animeshvarma.sigil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import dev.animeshvarma.sigil.ui.SigilApp
import dev.animeshvarma.sigil.ui.theme.SigilTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enables full screen (behind status bar)
        setContent {
            SigilTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    SigilApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}