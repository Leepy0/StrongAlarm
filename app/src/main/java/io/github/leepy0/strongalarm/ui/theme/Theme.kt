package io.github.leepy0.strongalarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 색 토큰. 색은 의미에만 쓴다:
 * sun = 알람이 울리는 날·주요 동작, moon = 쉬는 날, ember = 조치가 필요한 상태. 나머지는 중립색
 */
@Immutable
data class AppColors(
    val bg: Color,          // 배경
    val surface: Color,     // 묶음·시트
    val surfaceHigh: Color, // 눌림·칩 배경
    val line: Color,        // 구분선·트랙
    val ink: Color,         // 주요 텍스트
    val mist: Color,        // 보조 텍스트
    val faint: Color,       // 비활성
    val sun: Color,         // 울림 채움(원·주요 버튼)
    val onSun: Color,       // sun 위 텍스트
    val sunText: Color,     // 울림 의미의 텍스트·아이콘
    val sunSoft: Color,     // sun 계열 옅은 배경
    val moon: Color,        // 쉼 텍스트·테두리
    val moonSoft: Color,    // moon 계열 옅은 배경
    val ember: Color,       // 경고
)

/** 밤 */
val DarkColors = AppColors(
    bg = Color(0xFF121831),
    surface = Color(0xFF1C2442),
    surfaceHigh = Color(0xFF263157),
    line = Color(0xFF2E3961),
    ink = Color(0xFFECEFF8),
    mist = Color(0xFF8E97B8),
    faint = Color(0xFF5A6388),
    sun = Color(0xFFFFB347),
    onSun = Color(0xFF2B1A00),
    sunText = Color(0xFFFFB347),
    sunSoft = Color(0xFF3D3222),
    moon = Color(0xFF9DB1E8),
    moonSoft = Color(0xFF263257),
    ember = Color(0xFFFF7A6B),
)

/** 새벽 (밝은 화면). 텍스트용 색은 4.5:1 이상 대비 */
val LightColors = AppColors(
    bg = Color(0xFFF3F5FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE8ECF6),
    line = Color(0xFFD5DBEA),
    ink = Color(0xFF151B33),
    mist = Color(0xFF59627F),
    faint = Color(0xFF9AA2BC),
    sun = Color(0xFFFFB347),
    onSun = Color(0xFF2B1A00),
    sunText = Color(0xFF995800),
    sunSoft = Color(0xFFFFEFD6),
    moon = Color(0xFF4A5FA6),
    moonSoft = Color(0xFFE1E7F7),
    ember = Color(0xFFC9382A),
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

/** 화면 코드에서 쓰는 색 이름 (현재 테마의 토큰을 돌려줌) */
object Palette {
    val Night: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bg
    val Dusk: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
    val DuskHigh: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceHigh
    val Line: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.line
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink
    val Mist: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.mist
    val Faint: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.faint
    val Sun: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.sun
    val SunInk: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onSun
    val SunText: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.sunText
    val SunSoft: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.sunSoft
    val Moon: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.moon
    val MoonSoft: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.moonSoft
    val Ember: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ember
}

private fun scheme(c: AppColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.sun,
        onPrimary = c.onSun,
        primaryContainer = c.sunSoft,
        onPrimaryContainer = c.sunText,
        secondary = c.moon,
        onSecondary = c.bg,
        secondaryContainer = c.moonSoft,
        onSecondaryContainer = c.moon,
        tertiary = c.moon,
        background = c.bg,
        onBackground = c.ink,
        surface = c.bg,
        onSurface = c.ink,
        surfaceVariant = c.surface,
        onSurfaceVariant = c.mist,
        surfaceContainerLowest = c.bg,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.surfaceHigh,
        inverseSurface = c.ink,
        inverseOnSurface = c.bg,
        inversePrimary = c.sunText,
        outline = c.line,
        outlineVariant = c.line,
        error = c.ember,
        onError = c.bg,
    )
}

/**
 * 글자 크기 5단계(88/24/18/15/12), 굵기 2단계(400/600).
 * 88은 시각·걸음 같은 핵심 숫자 전용
 */
private fun typography(f: FontFamily?): Typography {
    val regular = FontWeight.Normal
    val semi = FontWeight.SemiBold
    fun s(size: Int, line: Int, w: FontWeight) = TextStyle(fontFamily = f, fontSize = size.sp, lineHeight = line.sp, fontWeight = w)
    val display = TextStyle(
        fontFamily = f, fontSize = 88.sp, lineHeight = 92.sp, fontWeight = regular,
        letterSpacing = (-2).sp, fontFeatureSettings = "tnum",
    )
    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = s(24, 32, semi),
        headlineMedium = s(24, 32, semi),
        headlineSmall = s(24, 32, semi),
        titleLarge = s(18, 24, semi),
        titleMedium = s(18, 24, semi),
        titleSmall = s(15, 20, semi),
        bodyLarge = s(15, 22, regular),
        bodyMedium = s(15, 22, regular),
        bodySmall = s(12, 18, regular),
        labelLarge = s(15, 20, semi),
        labelMedium = s(12, 16, semi),
        labelSmall = s(12, 16, semi),
    )
}

/** 모서리 3종: 8 / 16 / 완전 둥근 모양(CircleShape) */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * 기본은 시스템 다크 모드를 따름. 알람 화면은 어두운 방에서 보므로 dark = true 고정.
 * fontFamily는 스크린샷 테스트에서 한글 폰트 주입용
 */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), fontFamily: FontFamily? = null, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = scheme(colors, dark),
            typography = typography(fontFamily),
            shapes = AppShapes,
            content = content,
        )
    }
}
