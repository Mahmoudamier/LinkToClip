package com.mediaflow.universal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.mediaflow.universal.R
import com.mediaflow.universal.data.ThemeMode

val ArabicFont = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold)
)

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF006B5B),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF9DF2DC),
    secondary = androidx.compose.ui.graphics.Color(0xFF356A61),
    tertiary = androidx.compose.ui.graphics.Color(0xFF416A8C),
    surface = androidx.compose.ui.graphics.Color(0xFFFAFCFB),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFDDE9E4)
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF7BD8C1),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00382F),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF005344),
    secondary = androidx.compose.ui.graphics.Color(0xFFB3CCC6),
    tertiary = androidx.compose.ui.graphics.Color(0xFFA8C8E8),
    surface = androidx.compose.ui.graphics.Color(0xFF101615),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF26322F)
)

@Composable
fun MediaFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Fixed, calm palette: dynamic wallpaper colours made the interface look inconsistent.
    val colorScheme = if (dark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes
    ) {
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            content()
        }
    }
}
