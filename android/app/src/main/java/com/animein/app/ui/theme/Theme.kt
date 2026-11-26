package com.animein.app.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalAccentPreset = staticCompositionLocalOf { DefaultAccentPreset }
val LocalIsTV = staticCompositionLocalOf { false }
val LocalOpenAppearanceSettings = staticCompositionLocalOf<() -> Unit> { {} }

private fun buildDarkColorScheme(accent: AccentPreset) = darkColorScheme(
    primary = accent.accent,
    onPrimary = accent.onAccent,
    primaryContainer = accent.accentSoft,
    onPrimaryContainer = accent.accent,
    secondary = Surface3,
    onSecondary = FgPrimary,
    tertiary = FgSecondary,
    background = BgGraphite,
    onBackground = FgPrimary,
    surface = Surface1,
    onSurface = FgPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = FgSecondary,
    outline = HairlineStrong,
    outlineVariant = Hairline,
    error = DangerCoral,
    onError = BgGraphite
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun AnimeInTheme(
    accentId: String = DefaultAccentPreset.id,
    onOpenAppearanceSettings: () -> Unit = {},
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDark: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val accentPreset = accentPresetById(accentId)
    val isDark = if (forceDark) true else darkTheme
    val configuration = LocalConfiguration.current
    val isTV = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> buildDarkColorScheme(accentPreset)
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val bgArgb = colorScheme.background.toArgb()
            window.statusBarColor = bgArgb
            window.navigationBarColor = bgArgb
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalAccentPreset provides accentPreset,
        LocalIsTV provides isTV,
        LocalOpenAppearanceSettings provides onOpenAppearanceSettings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
