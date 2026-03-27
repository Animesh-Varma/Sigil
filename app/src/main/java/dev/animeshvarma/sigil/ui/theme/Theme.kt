package dev.animeshvarma.sigil.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

            // Clamp actual seed to opaque before reusing across the scheme
            val actualSeed = if (!darkTheme && isSeedWhite) Color.Black else Color(seedColor).copy(alpha = 1f)

            if (darkTheme) {
                val onSeed = actualSeed.calculateContrastColor()
                val darkSurface = Color(0xFF1E1E1E)

                val primaryContainerColor = actualSeed.copy(alpha = 0.3f)
                val secondaryContainerColor = actualSeed.copy(alpha = 0.2f)
                val tertiaryContainerColor = actualSeed.copy(alpha = 0.2f)

                darkColorScheme(
                    primary = actualSeed,
                    onPrimary = onSeed,
                    primaryContainer = primaryContainerColor,
                    onPrimaryContainer = primaryContainerColor.compositeOver(darkSurface).calculateContrastColor(),

                    secondary = actualSeed,
                    onSecondary = onSeed,
                    secondaryContainer = secondaryContainerColor,
                    onSecondaryContainer = secondaryContainerColor.compositeOver(darkSurface).calculateContrastColor(),

                    tertiary = actualSeed,
                    onTertiary = onSeed,
                    tertiaryContainer = tertiaryContainerColor,
                    onTertiaryContainer = tertiaryContainerColor.compositeOver(darkSurface).calculateContrastColor(),

                    background = Color(0xFF121212),
                    surface = darkSurface,
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray,
                    surfaceContainer = darkSurface,
                    surfaceContainerLow = Color(0xFF1A1A1A),
                    surfaceContainerHigh = Color(0xFF252525),

                    outline = actualSeed.copy(alpha = 0.6f),
                    outlineVariant = actualSeed.copy(alpha = 0.3f)
                )
            } else {
                val contrastText = actualSeed.calculateContrastColor()
                val lightSurface = Color(0xFFFFFBFE)

                val primaryContainerColor = actualSeed.copy(alpha = 0.2f)
                val secondaryContainerColor = actualSeed.copy(alpha = 0.1f)
                val tertiaryContainerColor = actualSeed.copy(alpha = 0.1f)

                lightColorScheme(
                    primary = actualSeed,
                    onPrimary = contrastText,
                    primaryContainer = primaryContainerColor,
                    onPrimaryContainer = primaryContainerColor.compositeOver(lightSurface).calculateContrastColor(),

                    secondary = actualSeed,
                    onSecondary = contrastText,
                    secondaryContainer = secondaryContainerColor,
                    onSecondaryContainer = secondaryContainerColor.compositeOver(lightSurface).calculateContrastColor(),

                    tertiary = actualSeed,
                    onTertiary = contrastText,
                    tertiaryContainer = tertiaryContainerColor,
                    onTertiaryContainer = tertiaryContainerColor.compositeOver(lightSurface).calculateContrastColor(),

                    background = lightSurface,
                    surface = lightSurface,
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