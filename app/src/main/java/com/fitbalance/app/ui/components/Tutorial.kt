package com.fitbalance.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.ui.theme.Brand

/**
 * 튜토리얼이 비출 위치를 모아 두는 곳.
 *
 * 화면 요소에 [tutorialTarget]을 붙이면 그 위치가 여기에 등록되고,
 * 오버레이가 키로 찾아 그 자리만 밝게 남긴다. 좌표를 코드에 박지 않아
 * 화면이 바뀌어도 구멍이 어긋나지 않는다.
 */
object TutorialTargets {
    private val rects = mutableStateMapOf<String, Rect>()

    operator fun get(key: String): Rect? = rects[key]

    internal fun put(key: String, rect: Rect) {
        rects[key] = rect
    }

    /** 화면을 벗어날 때 지운다. 남겨 두면 다른 화면에서 엉뚱한 자리가 밝아진다. */
    internal fun remove(key: String) {
        rects.remove(key)
    }
}

/** 이 요소를 튜토리얼에서 비출 대상으로 등록한다. */
fun Modifier.tutorialTarget(key: String): Modifier = this
    .onGloballyPositioned { coords ->
        val pos = coords.positionInRoot()
        TutorialTargets.put(
            key,
            Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height),
        )
    }

/**
 * 튜토리얼 한 장면.
 *
 * @param targetKey 비출 요소의 키. null이면 구멍 없이 설명만 띄운다(첫 장면 등).
 * @param body 한두 줄로 끝낸다. 길면 아무도 안 읽는다.
 */
data class TutorialStep(
    val targetKey: String?,
    val title: String,
    val body: String,
)

/**
 * 첫 실행 안내 오버레이.
 *
 * 화면 전체를 덮고 모든 터치를 먹는다. 실제 버튼은 눌리지 않고, 어디를 눌러도
 * 다음 장면으로만 넘어간다. 밑에 깔린 화면은 그대로 보이므로 어느 버튼을
 * 말하는지 눈으로 확인할 수 있다.
 */
@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    onFinish: () -> Unit,
) {
    if (steps.isEmpty()) return
    var index by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val target = step.targetKey?.let { TutorialTargets[it] }

    val density = LocalDensity.current
    val padPx = with(density) { 8.dp.toPx() }
    val cornerPx = with(density) { 18.dp.toPx() }

    // 구멍이 장면마다 부드럽게 옮겨 가도록 위치를 애니메이션한다.
    val holeLeft by animateFloatAsState(target?.left?.minus(padPx) ?: 0f, tween(280), label = "hl")
    val holeTop by animateFloatAsState(target?.top?.minus(padPx) ?: 0f, tween(280), label = "ht")
    val holeRight by animateFloatAsState(target?.right?.plus(padPx) ?: 0f, tween(280), label = "hr")
    val holeBottom by animateFloatAsState(target?.bottom?.plus(padPx) ?: 0f, tween(280), label = "hb")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // 모든 터치를 여기서 소비한다. 아래 화면의 버튼은 눌리지 않는다.
            // 손을 뗄 때만 한 장 넘긴다. 누를 때와 뗄 때를 다 세면 두 장씩 건너뛴다.
            .pointerInput(steps.size) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.type == PointerEventType.Release) {
                            if (index >= steps.lastIndex) onFinish() else index++
                        }
                    }
                }
            }
    ) {
        val screenH = with(density) { maxHeight.toPx() }

        // 어두운 막에 구멍을 뚫는다. BlendMode.Clear는 별도 레이어에서만 동작한다.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawRect(Color.Black.copy(alpha = 0.78f))
                    if (target != null) {
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(holeLeft, holeTop),
                            size = Size(holeRight - holeLeft, holeBottom - holeTop),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx),
                            blendMode = BlendMode.Clear,
                        )
                    }
                }
        )

        // 설명은 구멍을 가리지 않는 쪽에 붙인다.
        val belowHole = target != null && holeBottom < screenH * 0.62f
        val bubbleAlign = when {
            target == null -> Alignment.Center
            belowHole -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }

        Column(
            Modifier
                .align(bubbleAlign)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(MascotMood.SEARCHING, size = 54, bob = false)
                    HSpace(12)
                    Column(Modifier.width(210.dp)) {
                        Text(
                            step.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Brand.Ink,
                        )
                        VSpace(4)
                        Text(
                            step.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.Muted,
                        )
                    }
                }
            }

            VSpace(14)
            Row(verticalAlignment = Alignment.CenterVertically) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(width = if (i == index) 20.dp else 7.dp, height = 7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == index) Brand.MintBright else Color.White.copy(alpha = 0.35f)
                            )
                    )
                    HSpace(6)
                }
            }
            VSpace(12)
            // 밑에 깔린 화면 글자와 겹쳐 읽기 어려워지므로 자체 배경을 준다.
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    if (index >= steps.lastIndex) "화면을 누르면 시작합니다"
                    else "화면 아무 곳이나 누르세요",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}
