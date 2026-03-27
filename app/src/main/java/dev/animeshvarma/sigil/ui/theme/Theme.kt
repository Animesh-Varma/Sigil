package dev.animeshvarma.sigil.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private fun Color.calculateContrastColor(): Color {
    val l = luminance()
    val blackContrast = (l + 0.05f) / 0.05f
    val whiteContrast = 1.05f / (l + 0.05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

@Composable
fun SigilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Int? = null,
    content: @Composable () -> Unit
) {
    val useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseColorScheme = when {
        // Material You (Dynamic Colors) - Priority if enabled
        useDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // Custom Seed Color - "Standardized" Propagation
        seedColor != null -> {
            val isSeedWhite = seedColor == 0xFFFFFFFF.toInt()

            val actualSeed = if (!darkTheme && isSeedWhite) Color.Black else Color(seedColor)

            if (darkTheme) {
                val onSeed = actualSeed.calculateContrastColor()

                darkColorScheme(
                    primary = actualSeed,
                    onPrimary = onSeed,
                    primaryContainer = actualSeed.copy(alpha = 0.3f),
                    onPrimaryContainer = Color.White,

                    secondary = actualSeed,
                    onSecondary = onSeed,
                    secondaryContainer = actualSeed.copy(alpha = 0.2f),
                    onSecondaryContainer = Color.White,

                    tertiary = actualSeed,
                    onTertiary = onSeed,
                    tertiaryContainer = actualSeed.copy(alpha = 0.2f),
                    onTertiaryContainer = Color.White,

                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray,
                    surfaceContainer = Color(0xFF1E1E1E),
                    surfaceContainerLow = Color(0xFF1A1A1A),
                    surfaceContainerHigh = Color(0xFF252525),

                    outline = actualSeed.copy(alpha = 0.6f),
                    outlineVariant = actualSeed.copy(alpha = 0.3f)
                )
            } else {
                val contrastText = actualSeed.calculateContrastColor()

                lightColorScheme(
                    primary = actualSeed,
                    onPrimary = contrastText,
                    primaryContainer = actualSeed.copy(alpha = 0.2f),
                    onPrimaryContainer = Color.Black,
                    secondary = actualSeed,
                    onSecondary = contrastText,
                    secondaryContainer = actualSeed.copy(alpha = 0.1f),
                    onSecondaryContainer = Color.Black,
                    tertiary = actualSeed,
                    onTertiary = contrastText,
                    tertiaryContainer = actualSeed.copy(alpha = 0.1f),
                    onTertiaryContainer = Color.Black,
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    onSurface = Color.Black,
                    outline = actualSeed.copy(alpha = 0.5f)
                )
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val finalColorScheme = if (!useDynamicColor && seedColor != null) {
        baseColorScheme.copy(
            onPrimary = baseColorScheme.primary.calculateContrastColor(),
            onSecondary = baseColorScheme.secondary.calculateContrastColor(),
            onTertiary = baseColorScheme.tertiary.calculateContrastColor()
        )
    } else {
        baseColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = content
    )
}