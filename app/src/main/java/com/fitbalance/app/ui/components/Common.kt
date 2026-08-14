package com.fitbalance.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import com.fitbalance.app.data.FactorScore
import com.fitbalance.app.ui.theme.Brand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VSpace(dp: Int) = Spacer(Modifier.height(dp.dp))

@Composable
fun HSpace(dp: Int) = Spacer(Modifier.width(dp.dp))

/** 섹션 위에 얹는 작은 대문자 라벨. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = Brand.MintDeep,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 26.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

/** 흰 배경 카드. Material Card 대신 그림자·모서리를 직접 맞춘다. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Int = 20,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brand.Surface)
            .padding(padding.dp)
    ) { content() }
}

/** 화면 상단의 다크 히어로 블록. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brand.HeroGradient)
            .padding(22.dp)
    ) { content() }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Brand.PrimaryGradient else SolidColor(Color(0xFFDDE4E3)))
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color(0xFF04120D),
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color(0xFF9DABA9),
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 15.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Brand.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Brand.Muted,
            disabledContentColor = Brand.Muted2.copy(alpha = 0.6f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 어두운 배경 위 버튼(강좌 상세의 신청 버튼). */
@Composable
fun DarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Brand.Ink,
            contentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 15.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = Brand.MintSoft,
    fg: Color = Brand.MintDeep,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** 종합 점수 링. 히어로 카드 위에 얹는다. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Int = 78) {
    val progress by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(900),
        label = "score",
    )
    Canvas(modifier.size(size.dp)) {
        val stroke = 7.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(
            color = Color.White.copy(alpha = 0.13f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            brush = Brush.linearGradient(listOf(Brand.MintBright, Brand.MintDeep)),
            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * 체력 4요인 레이더 차트. 약점 축은 붉게 강조해 불균형이 한눈에 보이게 한다.
 * 축 라벨의 숫자는 화면 표기와 같은 "등수"(100 - 백분위)다.
 */
@Composable
fun RadarChart(
    factors: List<FactorScore>,
    weakFactors: List<String>,
    modifier: Modifier = Modifier,
    size: Int = 236,
) {
    val measurer = rememberTextMeasurer()
    val nameStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val nameWeakStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Brand.Coral)
    val rankStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Brand.Muted2)
    val rankWeakStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Brand.Coral)

    Canvas(modifier.size(size.dp)) {
        if (factors.isEmpty()) return@Canvas
        val n = factors.size
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val radius = minOf(cx, cy) - 46.dp.toPx()

        fun angle(i: Int) = (2.0 * PI * i / n) - PI / 2
        fun at(i: Int, t: Float) = Offset(
            cx + (cos(angle(i)) * radius * t).toFloat(),
            cy + (sin(angle(i)) * radius * t).toFloat(),
        )

        fun polygon(t: Float) = Path().apply {
            moveTo(at(0, t).x, at(0, t).y)
            for (i in 1 until n) lineTo(at(i, t).x, at(i, t).y)
            close()
        }

        // 격자
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { t ->
            drawPath(polygon(t), color = Color(0xFFE3EAE8), style = Stroke(width = 1.dp.toPx()))
        }
        // 축
        for (i in 0 until n) {
            drawLine(
                color = Color(0xFFE3EAE8),
                start = Offset(cx, cy), end = at(i, 1f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // 사용자 다각형
        val shape = Path().apply {
            val t0 = (factors[0].percentile / 100f).coerceAtLeast(0.06f)
            moveTo(at(0, t0).x, at(0, t0).y)
            for (i in 1 until n) {
                val t = (factors[i].percentile / 100f).coerceAtLeast(0.06f)
                lineTo(at(i, t).x, at(i, t).y)
            }
            close()
        }
        drawPath(
            shape,
            brush = Brush.linearGradient(
                listOf(Brand.Mint.copy(alpha = 0.34f), Brand.MintDeep.copy(alpha = 0.16f))
            ),
        )
        drawPath(shape, color = Brand.MintDeep, style = Stroke(width = 2.dp.toPx()))

        // 꼭짓점과 라벨
        factors.forEachIndexed { i, f ->
            val isWeak = f.factor in weakFactors
            val t = (f.percentile / 100f).coerceAtLeast(0.06f)
            val p = at(i, t)
            drawCircle(Color.White, radius = (if (isWeak) 5.5f else 4f).dp.toPx() + 2.dp.toPx(), center = p)
            drawCircle(
                if (isWeak) Brand.Coral else Brand.MintDeep,
                radius = (if (isWeak) 5.5f else 4f).dp.toPx(),
                center = p,
            )

            val labelAt = at(i, 1.28f)
            val name = measurer.measure(f.label, if (isWeak) nameWeakStyle else nameStyle)
            drawText(
                name,
                topLeft = Offset(labelAt.x - name.size.width / 2f, labelAt.y - name.size.height / 2f),
            )
            val rank = measurer.measure("${100 - f.percentile}등", if (isWeak) rankWeakStyle else rankStyle)
            drawText(
                rank,
                topLeft = Offset(
                    labelAt.x - rank.size.width / 2f,
                    labelAt.y + name.size.height / 2f + 1.dp.toPx(),
                ),
            )
        }
    }
}

/** 요인별 순위 막대. 값이 낮을수록 붉게 표시해 약점이 눈에 띄게 한다. */
@Composable
fun FactorBar(
    label: String,
    percentile: Int,
    grade: String,
    isWeak: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = Brand.tone(percentile)
    val width by animateFloatAsState(
        targetValue = percentile.coerceIn(3, 100) / 100f,
        animationSpec = tween(700),
        label = "bar",
    )
    Column(modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isWeak) {
                    HSpace(5)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brand.CoralSoft)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "약점",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Brand.Coral,
                        )
                    }
                }
            }
            Text(
                "${100 - percentile}등 · $grade",
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
        VSpace(6)
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brand.TrackBg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, message: String = "불러오는 중...") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Brand.MintDeep, strokeWidth = 3.dp)
            VSpace(14)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.CoralSoft)
                    .padding(horizontal = 15.dp, vertical = 13.dp)
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB3392A),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                VSpace(16)
                GhostButton("다시 시도", onRetry, Modifier.width(160.dp))
            }
        }
    }
}
