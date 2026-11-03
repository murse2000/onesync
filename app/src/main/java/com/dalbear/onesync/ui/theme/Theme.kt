package com.dalbear.onesync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 색은 "계기판" 느낌으로 잡았다. 깊은 남색 바탕 위에 신호용 파랑 하나,
 * 성공/경고만 보조색으로 쓴다. 상태를 읽는 앱이라 색이 곧 의미여야 한다.
 */
private val Ink = Color(0xFF0B0F16)
private val Slate = Color(0xFF121824)
private val Steel = Color(0xFF1A2230)
private val Mist = Color(0xFFE6ECF5)
private val Muted = Color(0xFF94A2B8)
private val SignalBlue = Color(0xFF6EA8FF)
private val DeepBlue = Color(0xFF16324F)
private val SignalTeal = Color(0xFF57D6B4)
private val SignalAmber = Color(0xFFF0B85A)
private val SignalRed = Color(0xFFFF8A8A)

private val DarkColors = darkColorScheme(
    primary = SignalBlue,
    onPrimary = Color(0xFF06121F),
    primaryContainer = DeepBlue,
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = SignalTeal,
    onSecondary = Color(0xFF04201A),
    tertiary = SignalAmber,
    background = Ink,
    onBackground = Mist,
    surface = Ink,
    onSurface = Mist,
    surfaceVariant = Steel,
    onSurfaceVariant = Muted,
    surfaceContainer = Slate,
    surfaceContainerHigh = Steel,
    outline = Color(0xFF2A3546),
    outlineVariant = Color(0xFF1C2532),
    error = SignalRed,
    onError = Color(0xFF2A0A0A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF08214D),
    secondary = Color(0xFF0E8068),
    onSecondary = Color.White,
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF121A26),
    surface = Color(0xFFF6F8FC),
    onSurface = Color(0xFF121A26),
    surfaceVariant = Color(0xFFE7EDF6),
    onSurfaceVariant = Color(0xFF54637A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F4FA),
    outline = Color(0xFFC6D1E0),
    outlineVariant = Color(0xFFE1E8F2),
    error = Color(0xFFB3261E),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    // 라벨은 본문 계열보다 한 단계 작게, 자간은 건드리지 않는다.
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun OneSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
