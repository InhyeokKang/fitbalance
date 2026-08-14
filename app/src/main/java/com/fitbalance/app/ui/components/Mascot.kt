package com.fitbalance.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.fitbalance.app.ui.theme.Brand

/**
 * 마스코트 표정. 진단 결과나 화면 상황에 따라 고른다.
 *
 * 캐릭터는 콩 모양 몸통 하나로 통일하고 표정·소품만 바꾼다.
 * 이미지 리소스를 쓰지 않고 Canvas로 그려서 해상도에 관계없이 선명하다.
 */
enum class MascotMood {
    /** 기본. 눈웃음 */
    HAPPY,

    /** 약점이 많을 때. 땀방울 */
    WORRIED,

    /** 아주 좋을 때. 반짝임 */
    PROUD,

    /** 빈 결과·검색 중. 돋보기 */
    SEARCHING,

    /** 운동 권유. 아령 */
    WORKOUT,
}

/**
 * 종합 점수로 표정을 고른다.
 *
 * 점수는 4개 요인 백분위의 평균이라 50이 또래 중간이다.
 * 중간 아래는 리포트 문구도 "약화형"으로 나가므로 걱정 표정을 맞춰 준다.
 */
fun moodOfScore(score: Int): MascotMood = when {
    score >= 70 -> MascotMood.PROUD
    score >= 55 -> MascotMood.HAPPY
    else -> MascotMood.WORRIED
}

/**
 * 콩 모양 마스코트.
 *
 * @param bodyColor 몸통 색. 어두운 카드 위에서는 밝은 민트를 쓴다.
 * @param bob 위아래로 살짝 떠다니는 애니메이션 사용 여부.
 */
@Composable
fun Mascot(
    mood: MascotMood = MascotMood.HAPPY,
    modifier: Modifier = Modifier,
    size: Int = 96,
    bodyColor: Color = Brand.Mint,
    bob: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "mascot")
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (bob) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    Canvas(modifier.size(size.dp)) {
        val s = this.size.minDimension
        translate(top = -offsetY * s * 0.035f) {
            drawMascot(mood, s, bodyColor)
        }
    }
}

private fun DrawScope.drawMascot(mood: MascotMood, s: Float, bodyColor: Color) {
    val cx = s / 2f
    val bodyW = s * 0.62f
    val bodyH = s * 0.70f
    val bodyTop = s * 0.16f
    val bodyRect = Rect(cx - bodyW / 2f, bodyTop, cx + bodyW / 2f, bodyTop + bodyH)
    val eyeY = bodyTop + bodyH * 0.40f
    val eyeDx = bodyW * 0.21f
    val ink = Brand.Ink

    // 그림자
    drawOval(
        color = Color.Black.copy(alpha = 0.07f),
        topLeft = Offset(cx - bodyW * 0.40f, bodyTop + bodyH + s * 0.02f),
        size = Size(bodyW * 0.80f, s * 0.055f),
    )

    // 다리
    val legY = bodyTop + bodyH - s * 0.01f
    listOf(-1f, 1f).forEach { dir ->
        drawLine(
            color = bodyColor,
            start = Offset(cx + dir * bodyW * 0.20f, legY),
            end = Offset(cx + dir * bodyW * 0.26f, legY + s * 0.09f),
            strokeWidth = s * 0.055f,
            cap = StrokeCap.Round,
        )
    }

    // 팔. WORKOUT일 때는 아령을 쥐도록 앞쪽으로 내린다.
    val armY = bodyTop + bodyH * 0.60f
    val armDown = if (mood == MascotMood.WORKOUT) s * 0.10f else s * 0.05f
    listOf(-1f, 1f).forEach { dir ->
        drawLine(
            color = bodyColor,
            start = Offset(cx + dir * bodyW * 0.44f, armY),
            end = Offset(cx + dir * bodyW * 0.56f, armY + armDown),
            strokeWidth = s * 0.048f,
            cap = StrokeCap.Round,
        )
    }

    // 몸통 (콩 모양: 위가 살짝 좁은 타원)
    drawOval(color = bodyColor, topLeft = bodyRect.topLeft, size = bodyRect.size)
    // 배 쪽 밝은 하이라이트
    drawOval(
        color = Color.White.copy(alpha = 0.22f),
        topLeft = Offset(cx - bodyW * 0.26f, bodyTop + bodyH * 0.42f),
        size = Size(bodyW * 0.52f, bodyH * 0.40f),
    )

    when (mood) {
        MascotMood.HAPPY, MascotMood.PROUD, MascotMood.WORKOUT -> {
            // 눈웃음: 위로 볼록한 호
            listOf(-1f, 1f).forEach { dir ->
                drawArc(
                    color = ink,
                    startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(cx + dir * eyeDx - s * 0.055f, eyeY - s * 0.045f),
                    size = Size(s * 0.11f, s * 0.09f),
                    style = Stroke(width = s * 0.032f, cap = StrokeCap.Round),
                )
            }
        }
        MascotMood.WORRIED -> {
            // 점 눈 + 걱정스러운 눈썹
            listOf(-1f, 1f).forEach { dir ->
                drawCircle(ink, radius = s * 0.030f, center = Offset(cx + dir * eyeDx, eyeY))
                drawLine(
                    color = ink,
                    start = Offset(cx + dir * eyeDx - dir * s * 0.045f, eyeY - s * 0.085f),
                    end = Offset(cx + dir * eyeDx + dir * s * 0.030f, eyeY - s * 0.055f),
                    strokeWidth = s * 0.024f,
                    cap = StrokeCap.Round,
                )
            }
        }
        MascotMood.SEARCHING -> {
            listOf(-1f, 1f).forEach { dir ->
                drawCircle(ink, radius = s * 0.032f, center = Offset(cx + dir * eyeDx, eyeY))
            }
        }
    }

    // 볼터치
    listOf(-1f, 1f).forEach { dir ->
        drawOval(
            color = Brand.Coral.copy(alpha = 0.38f),
            topLeft = Offset(cx + dir * bodyW * 0.32f - s * 0.045f, eyeY + s * 0.045f),
            size = Size(s * 0.09f, s * 0.055f),
        )
    }

    // 입
    val mouthY = eyeY + s * 0.10f
    when (mood) {
        MascotMood.WORRIED -> drawArc(
            color = ink,
            startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(cx - s * 0.045f, mouthY),
            size = Size(s * 0.09f, s * 0.07f),
            style = Stroke(width = s * 0.026f, cap = StrokeCap.Round),
        )
        else -> drawArc(
            color = ink,
            startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(cx - s * 0.05f, mouthY - s * 0.025f),
            size = Size(s * 0.10f, s * 0.075f),
            style = Stroke(width = s * 0.026f, cap = StrokeCap.Round),
        )
    }

    // 소품
    when (mood) {
        MascotMood.WORRIED -> drawSweatDrop(cx + bodyW * 0.46f, bodyTop + bodyH * 0.16f, s)
        MascotMood.PROUD -> {
            drawSparkle(cx + bodyW * 0.52f, bodyTop + bodyH * 0.10f, s * 0.075f)
            drawSparkle(cx - bodyW * 0.55f, bodyTop + bodyH * 0.30f, s * 0.050f)
        }
        MascotMood.SEARCHING -> drawMagnifier(cx + bodyW * 0.50f, bodyTop + bodyH * 0.62f, s)
        // 팔 끝 높이에 맞춰 몸통 앞으로 겹쳐 그린다. 몸통 한가운데면 관통한 것처럼 보인다.
        MascotMood.WORKOUT -> drawDumbbell(cx, bodyTop + bodyH * 0.60f + s * 0.10f, bodyW, s)
        MascotMood.HAPPY -> Unit
    }
}

private fun DrawScope.drawSweatDrop(x: Float, y: Float, s: Float) {
    val r = s * 0.042f
    val path = Path().apply {
        moveTo(x, y - r * 1.7f)
        cubicTo(x + r * 1.2f, y - r * 0.2f, x + r, y + r, x, y + r)
        cubicTo(x - r, y + r, x - r * 1.2f, y - r * 0.2f, x, y - r * 1.7f)
        close()
    }
    drawPath(path, color = Color(0xFF6EC6FF))
}

private fun DrawScope.drawSparkle(x: Float, y: Float, r: Float) {
    val path = Path().apply {
        moveTo(x, y - r)
        cubicTo(x + r * 0.18f, y - r * 0.18f, x + r * 0.18f, y - r * 0.18f, x + r, y)
        cubicTo(x + r * 0.18f, y + r * 0.18f, x + r * 0.18f, y + r * 0.18f, x, y + r)
        cubicTo(x - r * 0.18f, y + r * 0.18f, x - r * 0.18f, y + r * 0.18f, x - r, y)
        cubicTo(x - r * 0.18f, y - r * 0.18f, x - r * 0.18f, y - r * 0.18f, x, y - r)
        close()
    }
    drawPath(path, color = Brand.Amber)
}

private fun DrawScope.drawMagnifier(x: Float, y: Float, s: Float) {
    val r = s * 0.085f
    rotate(degrees = 20f, pivot = Offset(x, y)) {
        drawLine(
            color = Brand.Ink,
            start = Offset(x + r * 0.7f, y + r * 0.7f),
            end = Offset(x + r * 1.9f, y + r * 1.9f),
            strokeWidth = s * 0.030f,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.White.copy(alpha = 0.85f), radius = r, center = Offset(x, y))
        drawCircle(Brand.Ink, radius = r, center = Offset(x, y), style = Stroke(width = s * 0.026f))
    }
}

private fun DrawScope.drawDumbbell(cx: Float, y: Float, bodyW: Float, s: Float) {
    val halfBar = bodyW * 0.56f
    drawLine(
        color = Brand.Ink,
        start = Offset(cx - halfBar, y),
        end = Offset(cx + halfBar, y),
        strokeWidth = s * 0.034f,
        cap = StrokeCap.Round,
    )
    val plateW = s * 0.075f
    val plateH = s * 0.165f
    listOf(-1f, 1f).forEach { dir ->
        drawRoundRectCompat(
            color = Brand.Ink,
            left = cx + dir * halfBar - plateW / 2f,
            top = y - plateH / 2f,
            width = plateW,
            height = plateH,
            radius = s * 0.026f,
        )
    }
}

private fun DrawScope.drawRoundRectCompat(
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    )
}
