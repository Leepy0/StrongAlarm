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
 * 색 토큰 (iOS 시스템 색 기준). 이름은 기존 의미를 유지:
 * sun = 주요 동작·울림(systemBlue), moon = 쉬는 날(systemIndigo), ember = 조치 필요(systemRed). 나머지는 중립색
 */
@Immutable
data class AppColors(
    val bg: Color,          // 그룹 배경 (systemGroupedBackground)
    val surface: Color,     // 셀·시트 (secondarySystemGroupedBackground)
    val surfaceHigh: Color, // 눌림·보조 버튼 배경 (tertiary fill)
    val line: Color,        // 구분선 (separator)
    val ink: Color,         // 주요 텍스트 (label)
    val mist: Color,        // 보조 텍스트 (secondaryLabel)
    val faint: Color,       // 비활성 (tertiaryLabel)
    val sun: Color,         // 주요 버튼·진행 채움 (accent)
    val onSun: Color,       // sun 위 텍스트
    val sunText: Color,     // 강조 텍스트·아이콘
    val sunSoft: Color,     // sun 계열 옅은 배경
    val moon: Color,        // 쉼 텍스트·테두리
    val moonSoft: Color,    // moon 계열 옅은 배경
    val ember: Color,       // 경고 (systemRed)
    val green: Color,       // 스위치 켜짐 (systemGreen)
)

/** 다크 모드: 순검정 배경 + #1C1C1E 셀 */
val DarkColors = AppColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceHigh = Color(0xFF2C2C2E),
    line = Color(0xA6545458),
    ink = Color(0xFFFFFFFF),
    mist = Color(0xFF98989F),
    faint = Color(0xFF636366),
    sun = Color(0xFF0A84FF),
    onSun = Color(0xFFFFFFFF),
    sunText = Color(0xFF0A84FF),
    sunSoft = Color(0x330A84FF),
    moon = Color(0xFF7D7AFF),
    moonSoft = Color(0x337D7AFF),
    ember = Color(0xFFFF453A),
    green = Color(0xFF30D158),
)

/** 라이트 모드: #F2F2F7 배경 + 흰 셀 */
val LightColors = AppColors(
    bg = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE5E5EA),
    line = Color(0x4A3C3C43),
    ink = Color(0xFF000000),
    mist = Color(0xFF6C6C70),
    faint = Color(0xFFAEAEB2),
    sun = Color(0xFF007AFF),
    onSun = Color(0xFFFFFFFF),
    sunText = Color(0xFF007AFF),
    sunSoft = Color(0x26007AFF),
    moon = Color(0xFF5856D6),
    moonSoft = Color(0x265856D6),
    ember = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
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
    val Green: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.green
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
        // 버튼·입력칸 테두리는 3:1 이상(mist), 구분선은 옅게(line)
        outline = c.mist,
        outlineVariant = c.line,
        error = c.ember,
        onError = c.bg,
    )
}

/**
 * iOS 텍스트 스타일 대응: headlineLarge = Large Title 34, headlineMedium = Title1 28, headlineSmall = Title2 22,
 * titleLarge = Title3 20, titleMedium = Headline 17, titleSmall = Subhead 15 굵게,
 * bodyLarge = Body 17, bodyMedium = Subhead 15, bodySmall = Footnote 13, labelLarge = 버튼 17, labelMedium = Caption 12, labelSmall = Caption2 11.
 * display(88)는 시각·걸음 같은 핵심 숫자 전용, 얇은 숫자꼴
 */
private fun typography(f: FontFamily?): Typography {
    val regular = FontWeight.Normal
    val semi = FontWeight.SemiBold
    val bold = FontWeight.Bold
    fun s(size: Int, line: Int, w: FontWeight) = TextStyle(fontFamily = f, fontSize = size.sp, lineHeight = line.sp, fontWeight = w)
    val display = TextStyle(
        fontFamily = f, fontSize = 88.sp, lineHeight = 92.sp, fontWeight = FontWeight.Light,
        letterSpacing = (-2).sp, fontFeatureSettings = "tnum",
    )
    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = s(34, 41, bold),
        headlineMedium = s(28, 34, bold),
        headlineSmall = s(22, 28, bold),
        titleLarge = s(20, 25, semi),
        titleMedium = s(17, 22, semi),
        titleSmall = s(15, 20, semi),
        bodyLarge = s(17, 22, regular),
        bodyMedium = s(15, 20, regular),
        bodySmall = s(13, 18, regular),
        labelLarge = s(17, 22, semi),
        labelMedium = s(12, 16, regular),
        labelSmall = s(11, 13, semi),
    )
}

/** 모서리: 칩 6 / 작은 8 / 묶음 10 / 알림·시트 14 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(14.dp),
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
