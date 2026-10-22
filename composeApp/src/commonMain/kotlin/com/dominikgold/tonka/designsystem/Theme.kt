package com.dominikgold.tonka.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dominikgold.tonka.resources.Res
import com.dominikgold.tonka.resources.inter_medium
import com.dominikgold.tonka.resources.inter_regular
import com.dominikgold.tonka.resources.inter_semibold
import com.dominikgold.tonka.resources.playfair_display_medium
import com.dominikgold.tonka.resources.playfair_display_regular
import org.jetbrains.compose.resources.Font

// Pinned key roles from the spec; the remaining tones follow a terracotta (#9C4A2B) seed
private val LightColors = lightColorScheme(
    primary = Color(0xFF9C4A2B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCE),
    onPrimaryContainer = Color(0xFF3A0B00),
    inversePrimary = Color(0xFFFFB59B),
    secondary = Color(0xFF8A5A22),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBD),
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = Color(0xFF6E6B2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2EDA7),
    onTertiaryContainer = Color(0xFF1F1D00),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF2A1D17),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF2A1D17),
    surfaceVariant = Color(0xFFF5DED5),
    onSurfaceVariant = Color(0xFF53433D),
    surfaceTint = Color(0xFF9C4A2B),
    inverseSurface = Color(0xFF3F2D26),
    inverseOnSurface = Color(0xFFFFEDE6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF87736B),
    outlineVariant = Color(0xFFD8C2BA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F4),
    surfaceDim = Color(0xFFE8D6CE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF0EA),
    surfaceContainer = Color(0xFFF7EAE2),
    surfaceContainerHigh = Color(0xFFF1E4DC),
    surfaceContainerHighest = Color(0xFFECDFD7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5C1A02),
    primaryContainer = Color(0xFF7C3317),
    onPrimaryContainer = Color(0xFFFFDBCE),
    inversePrimary = Color(0xFF9C4A2B),
    secondary = Color(0xFFF5BB7F),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6C430B),
    onSecondaryContainer = Color(0xFFFFDCBD),
    tertiary = Color(0xFFD9D58E),
    onTertiary = Color(0xFF353200),
    tertiaryContainer = Color(0xFF54521A),
    onTertiaryContainer = Color(0xFFF6F1A8),
    background = Color(0xFF1C130F),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF1C130F),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF53433D),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceTint = Color(0xFFFFB59B),
    inverseSurface = Color(0xFFF1DFD8),
    inverseOnSurface = Color(0xFF3A2E29),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D85),
    outlineVariant = Color(0xFF53433D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF443732),
    surfaceDim = Color(0xFF1C130F),
    surfaceContainerLowest = Color(0xFF160E0A),
    surfaceContainerLow = Color(0xFF251B17),
    surfaceContainer = Color(0xFF29201B),
    surfaceContainerHigh = Color(0xFF342A25),
    surfaceContainerHighest = Color(0xFF3F3530),
)

@Composable
private fun serifFamily() = FontFamily(
    Font(Res.font.playfair_display_regular, FontWeight.Normal),
    Font(Res.font.playfair_display_medium, FontWeight.Medium),
)

@Composable
private fun sansFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
)

@Composable
private fun tonkaTypography(): Typography {
    val serif = serifFamily()
    val sans = sansFamily()
    fun style(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight) =
        TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight)
    return Typography(
        displayLarge = style(serif, 57, 64, FontWeight.Normal),
        displayMedium = style(serif, 45, 52, FontWeight.Normal),
        displaySmall = style(serif, 36, 44, FontWeight.Normal),
        headlineLarge = style(serif, 32, 40, FontWeight.Normal),
        headlineMedium = style(serif, 28, 36, FontWeight.Normal),
        headlineSmall = style(serif, 24, 32, FontWeight.Medium),
        titleLarge = style(serif, 22, 28, FontWeight.Medium),
        titleMedium = style(sans, 16, 24, FontWeight.Medium),
        titleSmall = style(sans, 14, 20, FontWeight.Medium),
        bodyLarge = style(sans, 16, 24, FontWeight.Normal),
        bodyMedium = style(sans, 14, 20, FontWeight.Normal),
        bodySmall = style(sans, 12, 16, FontWeight.Normal),
        labelLarge = style(sans, 14, 20, FontWeight.Medium),
        labelMedium = style(sans, 12, 16, FontWeight.Medium),
        labelSmall = style(sans, 11, 16, FontWeight.Medium),
    )
}

// Dynamic color stays off so the brand palette holds on Android 12+
@Composable
fun TonkaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = tonkaTypography(),
        content = content,
    )
}
