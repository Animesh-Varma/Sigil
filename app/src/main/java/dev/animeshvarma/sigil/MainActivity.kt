package dev.animeshvarma.sigil

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import dev.animeshvarma.sigil.ui.SigilApp
import dev.animeshvarma.sigil.ui.OnboardingOrchestrator
import dev.animeshvarma.sigil.ui.theme.SigilTheme
import dev.animeshvarma.sigil.util.SigilPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[SigilViewModel::class.java]
        val prefs = SigilPreferences(this)

        val showOnboarding = mutableStateOf(!prefs.hasCompletedOnboarding())

        checkAndProcessIntent(intent, viewModel)

        setContent {
            SigilTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {

                        SigilApp(viewModel = viewModel)

                        if (showOnboarding.value) {
                            OnboardingOrchestrator(
                                viewModel = viewModel,
                                onComplete = {
                                    prefs.setOnboardingCompleted(true)
                                    showOnboarding.value = false
                                }
                            )
                        }
                    }
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
                intent.removeExtra(Intent.EXTRA_TEXT)
                viewModel.handleIncomingSharedText(sharedText)
            }
        }
    }
}