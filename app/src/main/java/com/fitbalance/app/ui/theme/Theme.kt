package com.fitbalance.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 브랜드 팔레트. 브라우저 미리보기(server/static/demo.html)와 같은 값을 쓴다.
object Brand {
    val Ink = Color(0xFF0C1112)
    val Ink2 = Color(0xFF182124)
    val Bg = Color(0xFFF2F5F4)
    val Surface = Color(0xFFFFFFFF)
    val Line = Color(0xFFE6EBEA)
    val Muted = Color(0xFF6B7A78)
    val Muted2 = Color(0xFF98A6A4)

    val Mint = Color(0xFF00D68F)
    val MintBright = Color(0xFF12F0A3)
    val MintDeep = Color(0xFF00A874)
    val MintSoft = Color(0xFFDFF9EF)

    val Coral = Color(0xFFFF6B5A)
    val CoralSoft = Color(0xFFFFEDEA)
    val Amber = Color(0xFFFFB020)

    val TrackBg = Color(0xFFEDF1F0)

    /** 주요 버튼 그라디언트. */
    val PrimaryGradient = Brush.linearGradient(listOf(MintBright, Color(0xFF00C283)))

    /** 다크 히어로 카드 배경. */
    val HeroGradient = Brush.linearGradient(
        listOf(Color(0xFF101A1B), Ink, Color(0xFF0F2420))
    )

    /** 백분위에 따른 색. 낮을수록 붉게. */
    fun tone(percentile: Int): Color = when {
        percentile >= 60 -> MintDeep
        percentile >= 40 -> Amber
        else -> Coral
    }
}

private val Colors = lightColorScheme(
    primary = Brand.MintDeep,
    onPrimary = Color(0xFF04120D),
    primaryContainer = Brand.MintSoft,
    onPrimaryContainer = Brand.MintDeep,
    secondary = Brand.Ink,
    onSecondary = Color.White,
    background = Brand.Bg,
    onBackground = Brand.Ink,
    surface = Brand.Surface,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.TrackBg,
    onSurfaceVariant = Brand.Muted,
    error = Brand.Coral,
    onError = Color.White,
    outline = Brand.Line,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// 자간을 좁혀 제목이 또렷하게 보이도록 한다.
private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp, lineHeight = 33.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp, lineHeight = 25.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 15.5.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)

@Composable
fun FitBalanceTheme(content: @Composable () -> Unit) {
    // 진단 리포트의 색 대비가 지표 판독에 직결되어 다크 테마는 두지 않는다.
    MaterialTheme(
        colorScheme = Colors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
