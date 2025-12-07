package dev.animeshvarma.sigil

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import dev.animeshvarma.sigil.ui.SigilApp
import dev.animeshvarma.sigil.ui.theme.SigilTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[SigilViewModel::class.java]

        checkAndProcessIntent(intent, viewModel)

        setContent {
            SigilTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SigilApp(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val viewModel = ViewModelProvider(this)[SigilViewModel::class.java]
        checkAndProcessIntent(intent, viewModel)
    }

    private fun checkAndProcessIntent(intent: Intent?, viewModel: SigilViewModel) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                // Clear any previous intent so it doesn't trigger again on rotation
                intent.removeExtra(Intent.EXTRA_TEXT)
                viewModel.handleIncomingSharedText(sharedText)
            }
        }
    }
}