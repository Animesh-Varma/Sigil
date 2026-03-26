package dev.animeshvarma.sigil

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import dev.animeshvarma.sigil.data.LockManager
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.ui.OnboardingOrchestrator
import dev.animeshvarma.sigil.ui.SigilApp
import dev.animeshvarma.sigil.ui.screens.LockScreen
import dev.animeshvarma.sigil.ui.theme.SigilTheme
import dev.animeshvarma.sigil.util.SigilPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var lockManager: LockManager
    private lateinit var viewModel: SigilViewModel
    private lateinit var prefs: SigilPreferences

    private val isContentHidden = mutableStateOf(true)

    /**
     * Initializes the activity UI, security, lifecycle observers, onboarding state, intent handling, and Compose content.
     *
     * Sets up edge-to-edge rendering, view model, preferences, and lock manager; applies screen security flags; initializes
     * the content-hidden state from the lock manager; registers a lifecycle observer to hide content on pause, record
     * background events and clear sensitive data on stop (when configured), and re-evaluate secure flag and content visibility
     * on start. Checks and processes incoming share intents, determines whether onboarding should be shown, and composes
     * the app UI: shows a lock screen when content is hidden and locking is enabled, otherwise displays the main app with
     * theming and onboarding orchestration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[SigilViewModel::class.java]
        prefs = SigilPreferences(this)
        lockManager = LockManager(this)

        updateSecureFlag()

        isContentHidden.value = lockManager.isAppLocked()

        // --- LIFECYCLE SECURITY OBSERVER ---
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (prefs.lockMode != LockMode.NONE) {
                        isContentHidden.value = true
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    lockManager.recordBackgroundEvent()

                    if (!prefs.isGracePeriodEnabled && prefs.lockMode != LockMode.NONE) {
                        viewModel.clearSensitiveData()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    updateSecureFlag()

                    if (lockManager.isAppLocked()) {
                        isContentHidden.value = true
                        viewModel.clearSensitiveData()
                    } else {
                        isContentHidden.value = false
                    }
                }
                else -> {}
            }
        })

        // Onboarding Check
        val isFirstLaunch = !prefs.hasCompletedOnboarding()
        val showOnboarding = mutableStateOf(isFirstLaunch)

        if (isFirstLaunch && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (prefs.isDarkModeEnabled) {
                viewModel.setDarkMode(false)
            }
        }

        checkAndProcessIntent(intent, viewModel)

        setContent {
            val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val systemDark = isSystemInDarkTheme()

            // 1. Force dynamic colors off on API < 31
            val actualDynamicColor = supportsDynamicColor && prefs.isDynamicColorsEnabled

            // 2. If Material You is ON, follow system. Otherwise, strictly follow the manual toggle!
            val useDarkTheme = if (actualDynamicColor) systemDark else prefs.isDarkModeEnabled

            SigilTheme(
                darkTheme = useDarkTheme,
                dynamicColor = actualDynamicColor,
                seedColor = prefs.selectedThemeColor
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {

                        val isLocked = isContentHidden.value && prefs.lockMode != LockMode.NONE

                        Crossfade(
                            targetState = isLocked,
                            animationSpec = tween(400),
                            label = "LockScreenTransition"
                        ) { locked ->
                            if (locked) {
                                LockScreen(
                                    viewModel = viewModel,
                                    onUnlock = {
                                        isContentHidden.value = false
                                        viewModel.consumePendingIntent()
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
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
            }
        }
    }

    /**
     * Handle a newly delivered intent, update the activity's current intent, and process any shared content it carries.
     *
     * @param intent The new Intent delivered to the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAndProcessIntent(intent, viewModel)
    }

    /**
     * Ensures sensitive in-memory data is cleared when saving instance state if a lock mode is enabled.
     *
     * @param outState Bundle in which to place saved state.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        if (prefs.lockMode != LockMode.NONE) {
            viewModel.clearSensitiveData()
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Processes an incoming ACTION_SEND "text/plain" intent by either handling its shared text immediately or caching it for later.
     *
     * If the intent contains EXTRA_TEXT, the extra is removed from the intent. If the app content is currently hidden or a lock mode is enabled, the shared text is cached via the view model and the content is marked hidden; otherwise the shared text is delivered to the view model for immediate handling. Non-matching or null intents are ignored.
     *
     * @param intent The incoming intent that may contain shared text (may be null).
     * @param viewModel The view model used to handle or cache the shared text.
     */
    private fun checkAndProcessIntent(intent: Intent?, viewModel: SigilViewModel) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                intent.removeExtra(Intent.EXTRA_TEXT)

                val shouldDefer = isContentHidden.value || lockManager.isAppLocked()
                if (shouldDefer) {
                    viewModel.cachePendingIntent(sharedText)
                    isContentHidden.value = true
                } else {
                    viewModel.handleIncomingSharedText(sharedText)
                }
            }
        }
    }

    private fun updateSecureFlag() {
        if (prefs.isScreenShieldEnabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}