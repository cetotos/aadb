package com.torpos.aadb.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlueContainer,
    onPrimary = SkyBlueDark,
    primaryContainer = SkyBlueContainerDark,
    onPrimaryContainer = SkyBlueContainer,
    secondary = TealContainer,
    onSecondary = TealDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = TealContainer,
    tertiary = EmberContainer,
    onTertiary = EmberDark,
    tertiaryContainer = EmberContainerDark,
    onTertiaryContainer = EmberContainer,
    background = Night,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightVariant,
    onSurfaceVariant = NightVariantText,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    inverseSurface = NightText,
    inverseOnSurface = NightSurface,
    inversePrimary = SkyBlue
)

private val LightColorScheme = lightColorScheme(
    primary = SkyBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = SkyBlueDark,
    secondary = Teal,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealDark,
    tertiary = Ember,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = EmberContainer,
    onTertiaryContainer = EmberDark,
    background = Mist,
    onBackground = MistText,
    surface = MistSurface,
    onSurface = MistText,
    surfaceVariant = MistVariant,
    onSurfaceVariant = MistVariantText,
    outline = MistOutline,
    outlineVariant = MistOutlineVariant,
    inverseSurface = NightSurface,
    inverseOnSurface = NightText,
    inversePrimary = SkyBlueContainerDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
