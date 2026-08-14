package com.fitbalance.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.BuildConfig
import com.fitbalance.app.data.Course
import com.fitbalance.app.data.RecommendResponse
import com.fitbalance.app.ui.Settings
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.Chip
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.HSpace
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 위도 1도의 거리(km). 경도는 cos(위도)로 눌러 같은 척도로 맞춘다. */
private const val KM_PER_DEG = 111.32

/**
 * 위경도를 화면 좌표로 옮기는 정사각 투영.
 *
 * 서울 정도의 좁은 범위에서는 경도에 cos(위도)만 곱해도 왜곡이 눈에 띄지 않아
 * 지도 SDK 없이도 상대 위치가 정확하게 나온다.
 */
private class Projection(
    private val originX: Double,
    private val originY: Double,
    private val scale: Double,
    private val offsetX: Float,
    private val offsetY: Float,
    private val cosLat: Double,
) {
    fun toScreen(lat: Double, lng: Double): Offset = Offset(
        (offsetX + (lng * cosLat - originX) * scale).toFloat(),
        (offsetY + (originY - lat) * scale).toFloat(),
    )

    /** 킬로미터를 화면 픽셀로. 반경·회랑 폭을 그릴 때 쓴다. */
    fun kmToPx(km: Double): Float = (km / KM_PER_DEG * scale).toFloat()

    companion object {
        /** 주어진 지점들이 모두 [size] 안에 들어오도록 투영을 만든다. */
        fun fit(points: List<Pair<Double, Double>>, size: IntSize, padPx: Float): Projection? {
            if (points.isEmpty() || size.width == 0 || size.height == 0) return null

            val meanLat = points.sumOf { it.first } / points.size
            val cosLat = cos(meanLat * PI / 180.0)

            val xs = points.map { it.second * cosLat }
            val ys = points.map { -it.first }
            var minX = xs.min(); var maxX = xs.max()
            var minY = ys.min(); var maxY = ys.max()

            // 점이 한 곳에 몰려 폭이 0이면 나눗셈이 무너진다. 최소 범위를 준다.
            val minSpan = 0.004
            if (maxX - minX < minSpan) { val c = (maxX + minX) / 2; minX = c - minSpan / 2; maxX = c + minSpan / 2 }
            if (maxY - minY < minSpan) { val c = (maxY + minY) / 2; minY = c - minSpan / 2; maxY = c + minSpan / 2 }

            val usableW = size.width - padPx * 2
            val usableH = size.height - padPx * 2
            if (usableW <= 0 || usableH <= 0) return null

            val scale = min(usableW / (maxX - minX), usableH / (maxY - minY))
            // 남는 쪽은 가운데로 민다.
            val drawnW = (maxX - minX) * scale
            val drawnH = (maxY - minY) * scale
            return Projection(
                originX = minX,
                originY = -minY,
                scale = scale,
                offsetX = (padPx + (usableW - drawnW) / 2).toFloat(),
                offsetY = (padPx + (usableH - drawnH) / 2).toFloat(),
                cosLat = cosLat,
            )
        }
    }
}

@Composable
fun MapScreen(
    state: UiState<RecommendResponse>,
    settings: Settings,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCourseClick: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        when (state) {
            is UiState.Loading, UiState.Idle ->
                LoadingBox(message = "퇴근 동선 안의 강좌를 찾는 중...", mood = MascotMood.SEARCHING)

            is UiState.Error -> ErrorBox(state.message, onRetry = onRetry)

            is UiState.Success -> MapBody(state.data, settings, onBack, onCourseClick)
        }
    }
}

@Composable
private fun MapBody(
    r: RecommendResponse,
    settings: Settings,
    onBack: () -> Unit,
    onCourseClick: (String) -> Unit,
) {
    var selected by remember(r) { mutableStateOf(r.items.firstOrNull()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        BrandBar()
        Eyebrow("STEP 2")
        VSpace(6)
        Text("퇴근길 지도", style = MaterialTheme.typography.titleLarge)
        VSpace(12)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Chip("강좌 ${r.total}개")
            Chip("동선 ${r.query.maxDistanceKm}km 이내", bg = Brand.TrackBg, fg = Brand.Muted)
        }
        VSpace(12)

        if (r.items.isEmpty()) {
            EmptyMap(onBack)
            return@Column
        }

        // 카카오 지도를 먼저 시도하고, 실패하면 도식 지도로 넘어간다.
        // 시연 중 빈 화면이 뜨지 않게 하려는 안전장치다.
        var mapError by remember { mutableStateOf<String?>(null) }
        val useKakao = KakaoMapState.available && mapError == null
        val fallbackReason = mapError ?: KakaoMapState.reason.takeIf { !KakaoMapState.available }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (useKakao) {
                KakaoCourseMap(
                    courses = r.items,
                    settings = settings,
                    selected = selected,
                    onSelect = { selected = it },
                    onFailed = { mapError = it },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),
                )
            } else {
                MapCanvas(
                    courses = r.items,
                    settings = settings,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        fallbackReason?.let {
            VSpace(8)
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Muted2,
            )
        }

        VSpace(12)
        selected?.let { c ->
            SelectedCard(
                course = c,
                rank = r.items.indexOfFirst { it.courseId == c.courseId } + 1,
                onClick = { onCourseClick(c.courseId) },
            )
        }
        VSpace(9)
        GhostButton("목록으로", onBack)
        VSpace(16)
    }
}

@Composable
private fun MapCanvas(
    courses: List<Course>,
    settings: Settings,
    selected: Course?,
    onSelect: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()

    val work = settings.workLat to settings.workLng
    val home = settings.homeLat to settings.homeLng

    val padPx = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
    val projection = remember(courses, settings, canvasSize) {
        Projection.fit(
            points = buildList {
                add(work); add(home)
                courses.forEach { add(it.lat to it.lng) }
            },
            size = canvasSize,
            padPx = padPx,
        )
    }

    val markerGap = with(androidx.compose.ui.platform.LocalDensity.current) { 34.dp.toPx() }
    val pinGap = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    val edgeMargin = with(androidx.compose.ui.platform.LocalDensity.current) { 22.dp.toPx() }

    // 마커 위치를 미리 계산해 그리기와 탭 판정에 함께 쓴다.
    // 같은 시설의 강좌는 좌표가 완전히 같아 그대로 두면 하나만 보인다.
    val markers = remember(projection, courses, canvasSize) {
        val p = projection ?: return@remember emptyList()
        spreadOverlaps(
            raw = courses.map { it to p.toScreen(it.lat, it.lng) },
            fixed = listOf(p.toScreen(work.first, work.second), p.toScreen(home.first, home.second)),
            markerGap = markerGap,
            pinGap = pinGap,
            bounds = canvasSize,
            margin = edgeMargin,
        )
    }

    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFEFF4F2))
            .onSizeChanged { canvasSize = it }
            .pointerInput(markers) {
                detectTapGestures { tap ->
                    // 탭 지점에서 가장 가까운 마커를 고른다. 손가락 크기를 감안해 넉넉히 잡는다.
                    val hit = markers.minByOrNull { (_, pos) -> (pos - tap).getDistance() }
                    if (hit != null && (hit.second - tap).getDistance() <= 48.dp.toPx()) {
                        onSelect(hit.first)
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = projection ?: return@Canvas
            val workPt = p.toScreen(work.first, work.second)
            val homePt = p.toScreen(home.first, home.second)

            drawGrid()

            // 퇴근 회랑: 직장-집 직선에서 최대 거리만큼의 띠
            val corridorPx = p.kmToPx(settings.maxDistanceKm)
            drawLine(
                color = Brand.Mint.copy(alpha = 0.13f),
                start = workPt, end = homePt,
                strokeWidth = corridorPx * 2,
                cap = StrokeCap.Round,
            )
            // 동선 자체
            drawLine(
                color = Brand.MintDeep.copy(alpha = 0.55f),
                start = workPt, end = homePt,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(10.dp.toPx(), 7.dp.toPx())
                ),
            )

            // 강좌 마커. 선택된 것은 마지막에 그려 위로 올린다.
            markers.forEachIndexed { index, (course, pos) ->
                if (course.courseId != selected?.courseId) {
                    drawMarker(pos, index + 1, isSelected = false, measurer = measurer)
                }
            }
            markers.forEachIndexed { index, (course, pos) ->
                if (course.courseId == selected?.courseId) {
                    drawMarker(pos, index + 1, isSelected = true, measurer = measurer)
                }
            }

            drawEndpoint(workPt, "직장", measurer)
            drawEndpoint(homePt, "집", measurer)
        }
    }
}

/**
 * 겹친 마커를 밀어내 전부 보이게 한다.
 *
 * 같은 시설에서 열리는 강좌는 좌표가 완전히 같고, 직장·집 핀 라벨 아래 깔리는 마커도 있다.
 * 서로 밀어내되 직장·집([fixed])은 움직이지 않는 장애물로 두고, 캔버스 밖으로 나가지 않게 가둔다.
 * 표시용 보정이라 원래 좌표 자체는 바꾸지 않는다.
 */
private fun spreadOverlaps(
    raw: List<Pair<Course, Offset>>,
    fixed: List<Offset>,
    markerGap: Float,
    pinGap: Float,
    bounds: IntSize,
    margin: Float,
): List<Pair<Course, Offset>> {
    if (raw.isEmpty()) return raw
    val pos = raw.map { it.second }.toMutableList()

    repeat(80) {
        for (i in pos.indices) {
            for (j in i + 1 until pos.size) {
                val d = pos[j] - pos[i]
                val dist = hypot(d.x, d.y)
                if (dist >= markerGap) continue
                // 완전히 겹치면 방향이 없다. 인덱스별로 다른 각도를 줘 부채꼴로 퍼지게 한다.
                val dir = if (dist < 0.01f) {
                    val a = i * 2.399963f // 황금각. 균등하게 흩어진다
                    Offset(cos(a.toDouble()).toFloat(), kotlin.math.sin(a.toDouble()).toFloat())
                } else d / dist
                val push = (markerGap - dist) / 2f
                pos[i] = pos[i] - dir * push
                pos[j] = pos[j] + dir * push
            }
            for (f in fixed) {
                val d = pos[i] - f
                val dist = hypot(d.x, d.y)
                if (dist >= pinGap) continue
                val dir = if (dist < 0.01f) Offset(0f, 1f) else d / dist
                pos[i] = f + dir * pinGap
            }
            pos[i] = Offset(
                pos[i].x.coerceIn(margin, max(margin, bounds.width - margin)),
                pos[i].y.coerceIn(margin, max(margin, bounds.height - margin)),
            )
        }
    }
    return raw.mapIndexed { i, (course, _) -> course to pos[i] }
}

/** 지도처럼 보이도록 아주 옅은 격자를 깐다. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid() {
    val step = 44.dp.toPx()
    val color = Color(0xFFE2E9E7)
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarker(
    pos: Offset,
    rank: Int,
    isSelected: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val r = if (isSelected) 19.dp.toPx() else 14.dp.toPx()
    if (isSelected) {
        drawCircle(Brand.Mint.copy(alpha = 0.25f), radius = r + 9.dp.toPx(), center = pos)
    }
    drawCircle(Color.White, radius = r + 2.5.dp.toPx(), center = pos)
    drawCircle(
        if (isSelected) Brand.MintDeep else Brand.Mint,
        radius = r,
        center = pos,
    )
    val label = measurer.measure(
        "$rank",
        TextStyle(
            fontSize = if (isSelected) 14.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF04120D),
        ),
    )
    drawText(
        label,
        topLeft = Offset(pos.x - label.size.width / 2f, pos.y - label.size.height / 2f),
    )
}

/** 직장·집 표시. 마커와 구분되도록 어두운 알약 모양으로 둔다. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEndpoint(
    pos: Offset,
    label: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val text = measurer.measure(
        label,
        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White),
    )
    val padH = 9.dp.toPx()
    val padV = 5.dp.toPx()
    val w = text.size.width + padH * 2
    val h = text.size.height + padV * 2
    val top = pos.y - h - 10.dp.toPx()

    drawCircle(Color.White, radius = 7.dp.toPx(), center = pos)
    drawCircle(Brand.Ink, radius = 4.5.dp.toPx(), center = pos)
    drawRoundRect(
        color = Brand.Ink,
        topLeft = Offset(pos.x - w / 2, top),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2),
    )
    drawText(text, topLeft = Offset(pos.x - text.size.width / 2f, top + padV))
}

@Composable
private fun SelectedCard(course: Course, rank: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brand.Surface)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Brand.PrimaryGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$rank",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF04120D),
                    )
                }
                HSpace(10)
                Column(Modifier.weight(1f)) {
                    Text(
                        course.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        course.address ?: course.facility,
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted,
                    )
                }
                Text(
                    "${((course.score ?: 0.0) * 100).toInt()}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Brand.MintDeep,
                )
            }
            VSpace(10)
            Text(
                "${course.weekday} ${course.startTime} · 동선에서 ${course.distanceKm}km",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            VSpace(12)
            PrimaryButton("이 강좌 자세히 보기", onClick)
        }
    }
}

@Composable
private fun EmptyMap(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Mascot(MascotMood.SEARCHING, size = 110)
        VSpace(10)
        Text(
            "지도에 표시할 강좌가 없습니다",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        VSpace(6)
        Text(
            "추천 조건을 넓히면 지도에 나타납니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.Muted,
        )
        VSpace(18)
        GhostButton("목록으로", onBack, Modifier.fillMaxWidth(0.6f))
    }
}
