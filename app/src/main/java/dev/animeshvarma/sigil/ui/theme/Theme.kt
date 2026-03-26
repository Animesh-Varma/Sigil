package dev.animeshvarma.sigil.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
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

@Composable
fun SigilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Int? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 1. Material You (Dynamic Colors) - Priority if enabled
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // 2. Custom Seed Color - "Standardized" Propagation
        seedColor != null -> {
            val isSeedWhite = seedColor == 0xFFFFFFFF.toInt()

            val actualSeed = if (!darkTheme && isSeedWhite) Color.Black else Color(seedColor)

            if (darkTheme) {
                darkColorScheme(
                    primary = actualSeed,
                    onPrimary = if (isSeedWhite) Color.Black else Color.White,
                    primaryContainer = actualSeed.copy(alpha = 0.3f),
                    onPrimaryContainer = actualSeed,

                    secondary = actualSeed,
                    onSecondary = if (isSeedWhite) Color.Black else Color.White,
                    secondaryContainer = actualSeed.copy(alpha = 0.2f),
                    onSecondaryContainer = actualSeed,

                    tertiary = actualSeed,
                    onTertiary = if (isSeedWhite) Color.Black else Color.White,
                    tertiaryContainer = actualSeed.copy(alpha = 0.2f),
                    onTertiaryContainer = actualSeed,

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
                lightColorScheme(
                    primary = actualSeed,
                    onPrimary = Color.White,
                    primaryContainer = actualSeed.copy(alpha = 0.2f),
                    onPrimaryContainer = Color.Black,

                    secondary = actualSeed,
                    onSecondary = Color.White,
                    secondaryContainer = actualSeed.copy(alpha = 0.1f),
                    onSecondaryContainer = Color.Black,

                    tertiary = actualSeed,
                    onTertiary = Color.White,
                    tertiaryContainer = actualSeed.copy(alpha = 0.1f),
                    onTertiaryContainer = Color.Black,

                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    onSurface = Color.Black,

                    outline = actualSeed.copy(alpha = 0.5f)
                )
            }
        }

        // 3. Fallback Defaults
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
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
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}