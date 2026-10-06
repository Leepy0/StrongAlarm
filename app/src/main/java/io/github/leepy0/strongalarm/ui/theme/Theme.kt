package io.github.leepy0.strongalarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 밤 → 새벽 팔레트. 색은 의미를 가진다:
 * Sun = 알람이 울리는 날·주요 동작, Moon = 쉬는 날, Ember = 조치가 필요한 상태
 */
object Palette {
    val Night = Color(0xFF121831)      // 배경
    val Dusk = Color(0xFF1C2442)       // 묶음·시트 표면
    val DuskHigh = Color(0xFF263157)   // 눌림·선택 표면
    val Line = Color(0xFF2E3961)       // 구분선·트랙
    val Ink = Color(0xFFECEFF8)        // 본문
    val Mist = Color(0xFF8E97B8)       // 보조 텍스트
    val Sun = Color(0xFFFFB347)        // 울림
    val SunInk = Color(0xFF2B1A00)     // Sun 위 텍스트
    val SunSoft = Color(0xFF3D3222)    // Sun 계열 배경
    val Moon = Color(0xFF9DB1E8)       // 쉼
    val MoonSoft = Color(0xFF263257)   // Moon 계열 배경
    val Ember = Color(0xFFFF7A6B)      // 경고
}

private val Scheme = darkColorScheme(
    primary = Palette.Sun,
    onPrimary = Palette.SunInk,
    primaryContainer = Palette.SunSoft,
    onPrimaryContainer = Palette.Sun,
    secondary = Palette.Moon,
    onSecondary = Palette.Night,
    secondaryContainer = Palette.MoonSoft,
    onSecondaryContainer = Palette.Moon,
    tertiary = Palette.Moon,
    background = Palette.Night,
    onBackground = Palette.Ink,
    surface = Palette.Night,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Dusk,
    onSurfaceVariant = Palette.Mist,
    surfaceContainerLowest = Palette.Night,
    surfaceContainerLow = Palette.Dusk,
    surfaceContainer = Palette.Dusk,
    surfaceContainerHigh = Palette.Dusk,
    surfaceContainerHighest = Palette.DuskHigh,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Night,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Ember,
    onError = Palette.Night,
)

/** 시계 숫자는 가늘고 크게, 나머지는 절제된 위계 */
private fun typography(family: FontFamily?) = Typography(
    displayLarge = TextStyle(
        fontFamily = family, fontSize = 92.sp, lineHeight = 96.sp, fontWeight = FontWeight.Light,
        letterSpacing = (-3).sp, fontFeatureSettings = "tnum",
    ),
    displaySmall = TextStyle(
        fontFamily = family, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Light,
        letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum",
    ),
    headlineSmall = TextStyle(fontFamily = family, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = family, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = family, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = family, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = family, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** 밤에 쓰는 앱이라 다크 전용. fontFamily는 스크린샷 테스트에서 한글 폰트 주입용 */
@Composable
fun AppTheme(fontFamily: FontFamily? = null, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = typography(fontFamily), content = content)
}
