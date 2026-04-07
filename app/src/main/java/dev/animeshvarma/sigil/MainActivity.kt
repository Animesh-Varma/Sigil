package dev.animeshvarma.sigil

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.function.Consumer
import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.animeshvarma.sigil.data.LockManager
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.ui.OnboardingOrchestrator
import dev.animeshvarma.sigil.ui.SigilApp
import dev.animeshvarma.sigil.util.LocalSigilViewModel
import dev.animeshvarma.sigil.ui.screens.LockScreen
import dev.animeshvarma.sigil.ui.theme.SigilTheme
import dev.animeshvarma.sigil.util.SigilPreferences
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {

    private lateinit var lockManager: LockManager
    private lateinit var viewModel: SigilViewModel
    private lateinit var prefs: SigilPreferences

    private val isContentHidden = mutableStateOf(true)
    private val isAppInForeground = mutableStateOf(true)
    private val isWindowFocused = mutableStateOf(true)

    private var screenCaptureCallback: Any? = null

    private lateinit var secureOverlayView: FrameLayout

    private lateinit var displayManager: DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { checkActiveDisplays() }
        override fun onDisplayRemoved(displayId: Int) { checkActiveDisplays() }
        override fun onDisplayChanged(displayId: Int) { checkActiveDisplays() }
    }

    private fun checkActiveDisplays() {
        val displays = displayManager.displays
        var isRecording = false

        for (display in displays) {
            if (display.isValid && display.state == Display.STATE_ON) {
                val name = display.name.lowercase()
                if (name.contains("virtual") || name.contains("mirror") || name.contains("cast")) {
                    isRecording = true
                    break
                }
            }
        }

        if (isRecording && !viewModel.isScreenRecording.value && prefs.isScreenShieldEnabled) {
            viewModel.addLog("SECURITY ALERT: Screen mirroring or recording detected.")
            Toast.makeText(this, "Screen Recording Detected", Toast.LENGTH_LONG).show()
        }

        viewModel.setScreenRecordingState(isRecording)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[SigilViewModel::class.java]
        prefs = SigilPreferences(this)
        lockManager = LockManager(this)

        updateSecureFlag()
        setupScreenCaptureCallback()
        initSecureOverlay()

        if (Build.VERSION.SDK_INT < 35) {
            displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
            displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            checkActiveDisplays()
        }

        isContentHidden.value = lockManager.isAppLocked()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isAppInForeground.value = true
                    hideSecureOverlay()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isAppInForeground.value = false
                    // Executes synchronously to block the Recents menu snapshot
                    if (prefs.isScreenShieldEnabled) {
                        showSecureOverlay()
                    }
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

        val isFirstLaunch = !prefs.hasCompletedOnboarding()
        val showOnboarding = mutableStateOf(isFirstLaunch)

        if (isFirstLaunch && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && prefs.isDarkModeEnabled) {
            viewModel.setDarkMode(false)
        }

        checkAndProcessIntent(intent, viewModel)

        setContent {
            CompositionLocalProvider(LocalSigilViewModel provides viewModel) {

                val view = LocalView.current
                DisposableEffect(Unit) {
                    view.filterTouchesWhenObscured = true
                    onDispose { }
                }

                val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val systemDark = isSystemInDarkTheme()
                val actualDynamicColor = supportsDynamicColor && prefs.isDynamicColorsEnabled
                val useDarkTheme = if (actualDynamicColor) systemDark else prefs.isDarkModeEnabled

                val isExempt by viewModel.isSecureExempt.collectAsStateWithLifecycle()
                val isScreenRecording by viewModel.isScreenRecording.collectAsStateWithLifecycle()

                val isScreenShieldEnabled by viewModel.isScreenShieldEnabled.collectAsStateWithLifecycle()

                val applyBlur = isScreenShieldEnabled &&
                        (!isAppInForeground.value || (!isWindowFocused.value && !isExempt) || isScreenRecording)

                val blurRadius by animateDpAsState(
                    targetValue = if (applyBlur) 32.dp else 0.dp,
                    animationSpec = tween(if (applyBlur) 0 else 300),
                    label = "SecureBlur"
                )

                SigilTheme(
                    darkTheme = useDarkTheme,
                    dynamicColor = actualDynamicColor,
                    seedColor = prefs.selectedThemeColor
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {

                        // 1. MAIN APP CONTENT WITH BLUR
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Modifier.blur(radius = blurRadius)
                                    } else Modifier
                                )
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

                        // 2. COMPOSE SHIELD OVERLAY
                        AnimatedVisibility(
                            visible = applyBlur,
                            enter = fadeIn(tween(if (applyBlur) 0 else 300)),
                            exit = fadeOut(tween(300))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.background.copy(
                                            alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.6f else 1.0f
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Screen Shield Active",
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isWindowFocused.value = hasFocus

        if (hasFocus) {
            updateSecureFlag()
        }
        // Compose state naturally reacts to isWindowFocused now, ensuring perfect sync.
    }

    override fun onDestroy() {
        super.onDestroy()
        teardownScreenCaptureCallback()
        if (Build.VERSION.SDK_INT < 35 && ::displayManager.isInitialized) {
            displayManager.unregisterDisplayListener(displayListener)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAndProcessIntent(intent, viewModel)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (prefs.lockMode != LockMode.NONE) {
            viewModel.clearSensitiveData()
        }
        super.onSaveInstanceState(outState)
    }

    // --- NATIVE RECENTS SHIELD ---
    private fun initSecureOverlay() {
        secureOverlayView = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Pure dark background for maximum Recents menu security
            setBackgroundColor("#121212".toColorInt())
            elevation = 1000f

            val icon = ImageView(this@MainActivity).apply {
                setImageResource(android.R.drawable.ic_lock_idle_lock) // Generic lock icon fallback
                layoutParams = FrameLayout.LayoutParams(150, 150, Gravity.CENTER)
                setColorFilter("#808080".toColorInt())
            }
            addView(icon)
        }
    }

    private fun showSecureOverlay() {
        val root = window.decorView as ViewGroup
        if (secureOverlayView.parent == null) {
            root.addView(secureOverlayView)
        }
    }

    private fun hideSecureOverlay() {
        val root = window.decorView as ViewGroup
        if (secureOverlayView.parent != null) {
            root.removeView(secureOverlayView)
        }
    }

    private fun updateSecureFlag() {
        if (prefs.isScreenShieldEnabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private var screenRecordingCallback: Consumer<Int>? = null

    @SuppressLint("MissingPermission")
    private fun setupScreenCaptureCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val captureCb = ScreenCaptureCallback {
                if (prefs.isScreenShieldEnabled) {
                    viewModel.addLog("SECURITY ALERT: OS Capture attempt detected.")
                    viewModel.clearClipboardSecurely()
                }
            }
            screenCaptureCallback = captureCb
            registerScreenCaptureCallback(mainExecutor, captureCb)
        }

        if (Build.VERSION.SDK_INT >= 35) {
            val recordingCb = Consumer<Int> { state ->
                val isRecording = (state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)

                if (isRecording && !viewModel.isScreenRecording.value && prefs.isScreenShieldEnabled) {
                    viewModel.addLog("SECURITY ALERT: System screen recording active.")
                    Toast.makeText(this, "Screen Recording Detected", Toast.LENGTH_LONG).show()
                }
                viewModel.setScreenRecordingState(isRecording)
            }
            screenRecordingCallback = recordingCb

            windowManager.addScreenRecordingCallback(mainExecutor, recordingCb)
        }
    }

    @SuppressLint("MissingPermission")
    private fun teardownScreenCaptureCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureCallback?.let {
                unregisterScreenCaptureCallback(it as ScreenCaptureCallback)
            }
        }
        if (Build.VERSION.SDK_INT >= 35) {
            screenRecordingCallback?.let {
                windowManager.removeScreenRecordingCallback(it)
            }
        }
    }

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
}