package jp.essential.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F6700),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F894),
    onPrimaryContainer = EssentialInk,
    secondary = Color(0xFF8A4B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB8),
    onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Color(0xFFA12B12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD1),
    onTertiaryContainer = Color(0xFF3D0700),
    background = EssentialCream,
    onBackground = EssentialInk,
    surface = Color(0xFFFFFBF2),
    onSurface = EssentialInk,
    surfaceVariant = Color(0xFFF0EBD9),
    onSurfaceVariant = Color(0xFF4A493F),
    outline = Color(0xFF7B7A6D),
    outlineVariant = Color(0xFFCCC8B8),
)

val LocalEssentialDark = staticCompositionLocalOf { false }

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD1BCFF),
    onPrimary = Color(0xFF30105C),
    primaryContainer = Color(0xFF49307C),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFFFB86E),
    onSecondary = Color(0xFF492900),
    secondaryContainer = Color(0xFF263D82),
    onSecondaryContainer = Color(0xFFDDE4FF),
    tertiary = Color(0xFFFFB4A1),
    onTertiary = Color(0xFF611300),
    tertiaryContainer = Color(0xFF852000),
    onTertiaryContainer = Color(0xFFFFDAD1),
    background = Color(0xFF100B23),
    onBackground = Color(0xFFE5E4D8),
    surface = Color(0xFF19152F),
    onSurface = Color(0xFFF1EBFF),
    surfaceVariant = Color(0xFF34304B),
    onSurfaceVariant = Color(0xFFD0C7E1),
    outline = Color(0xFF909487),
    outlineVariant = Color(0xFF43483A),
)

private val EssentialTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 38.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = 21.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Bold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
    ),
)

private val EssentialShapes = Shapes(
    extraSmall = RoundedCornerShape(10),
    small = RoundedCornerShape(16),
    medium = RoundedCornerShape(24),
    large = RoundedCornerShape(32),
    extraLarge = RoundedCornerShape(40),
)

@Composable
fun EssentialTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val target = if (darkTheme) DarkColors else LightColors
    // 文字と面も背景と同時に補間し、切り替え途中だけ白文字が白い面へ重なるのを避ける。
    val colors = target.copy(
        primary = animateThemeColor(target.primary),
        onPrimary = animateThemeColor(target.onPrimary),
        primaryContainer = animateThemeColor(target.primaryContainer),
        onPrimaryContainer = animateThemeColor(target.onPrimaryContainer),
        secondary = animateThemeColor(target.secondary),
        onSecondary = animateThemeColor(target.onSecondary),
        secondaryContainer = animateThemeColor(target.secondaryContainer),
        onSecondaryContainer = animateThemeColor(target.onSecondaryContainer),
        tertiary = animateThemeColor(target.tertiary),
        onTertiary = animateThemeColor(target.onTertiary),
        tertiaryContainer = animateThemeColor(target.tertiaryContainer),
        onTertiaryContainer = animateThemeColor(target.onTertiaryContainer),
        background = animateThemeColor(target.background),
        onBackground = animateThemeColor(target.onBackground),
        surface = animateThemeColor(target.surface),
        onSurface = animateThemeColor(target.onSurface),
        surfaceVariant = animateThemeColor(target.surfaceVariant),
        onSurfaceVariant = animateThemeColor(target.onSurfaceVariant),
        outline = animateThemeColor(target.outline),
        outlineVariant = animateThemeColor(target.outlineVariant),
    )
    CompositionLocalProvider(LocalEssentialDark provides darkTheme) {
    MaterialTheme(
        colorScheme = colors,
        typography = EssentialTypography,
        shapes = EssentialShapes,
        content = content,
    )
    }
}

@Composable
private fun animateThemeColor(color: Color): Color =
    animateColorAsState(color, tween(700), label = "テーマ配色の補間").value
