package com.fitbalance.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fitbalance.app.data.Course
import com.fitbalance.app.ui.Settings
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.kakao.vectormap.route.RouteLineStylesSet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 같은 시설에서 열리는 강좌 묶음.
 *
 * 좌표가 완전히 같아 마커를 그대로 찍으면 하나만 보인다. 몇십 미터 밀어내도
 * 도시 축척에서는 몇 픽셀이라 소용이 없다. 그래서 시설당 마커 하나로 묶고
 * 개수를 함께 표시한 뒤, 탭할 때마다 묶음 안에서 다음 강좌로 넘긴다.
 */
private class FacilityGroup(
    val labelId: String,
    val position: LatLng,
    /** 매칭 순위 순서. 첫 번째가 이 시설의 대표 강좌다. */
    val courses: List<Course>,
    /** 각 강좌의 목록상 순위(1부터). */
    val ranks: List<Int>,
)

private fun groupByFacility(courses: List<Course>): List<FacilityGroup> =
    courses
        .withIndex()
        .groupBy { (_, c) -> "%.5f,%.5f".format(c.lat, c.lng) }
        .entries
        .mapIndexed { groupIndex, (_, entries) ->
            FacilityGroup(
                labelId = "group_$groupIndex",
                position = LatLng.from(entries.first().value.lat, entries.first().value.lng),
                courses = entries.map { it.value },
                ranks = entries.map { it.index + 1 },
            )
        }

/** 지도에 올린 것들을 나중에 갱신하려고 들고 있는 묶음. */
private class MapHandles(
    val map: KakaoMap,
    val layer: LabelLayer,
    /** 강좌 id별 (기본, 선택) 스타일. 순위 숫자가 박혀 있어 강좌마다 다르다. */
    val styles: Map<String, Pair<LabelStyles, LabelStyles>>,
)

/**
 * 마커 비트맵을 직접 그린다.
 *
 * 벡터 드로어블(LabelStyle.from(resId))은 지도 라벨로 올려도 화면에 나오지 않는다.
 * 네이티브 렌더러가 비트맵을 요구하므로 여기서 만들어 넘긴다.
 * 순위 숫자를 함께 그려 목록과 지도를 눈으로 맞출 수 있게 한다.
 */
private fun markerBitmap(
    density: Float,
    rank: Int,
    selected: Boolean,
    /** 같은 시설의 강좌 수. 2 이상이면 오른쪽 위에 개수 배지를 붙인다. */
    courseCount: Int = 1,
): Bitmap {
    val sizeDp = if (selected) 44f else 34f
    val px = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 배지 자리를 남기려고 본체는 살짝 왼쪽 아래로 그린다.
    val bodyR = px * (if (selected) 0.36f else 0.34f)
    val cx = px * 0.44f
    val cy = px * 0.56f

    if (selected) {
        paint.color = 0x4000D68F
        canvas.drawCircle(cx, cy, bodyR * 1.30f, paint)
    }
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(cx, cy, bodyR * 1.14f, paint)
    paint.color = if (selected) 0xFF00A874.toInt() else 0xFF00D68F.toInt()
    canvas.drawCircle(cx, cy, bodyR, paint)

    paint.color = 0xFF04120D.toInt()
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = bodyR * 1.05f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(rank.toString(), cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)

    if (courseCount > 1) {
        val br = px * 0.19f
        val bx = px - br - px * 0.03f
        val by = br + px * 0.03f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(bx, by, br * 1.18f, paint)
        paint.color = 0xFF0C1112.toInt()
        canvas.drawCircle(bx, by, br, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = br * 1.15f
        canvas.drawText("$courseCount", bx, by - (paint.descent() + paint.ascent()) / 2f, paint)
    }
    return bitmap
}

/** 직장·집 표시. 강좌 마커와 구분되도록 어두운 원으로 그린다. */
private fun endpointBitmap(density: Float, label: String): Bitmap {
    val px = (26f * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = px / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(c, c, c, paint)
    paint.color = 0xFF0C1112.toInt()
    canvas.drawCircle(c, c, c * 0.80f, paint)
    paint.color = 0xFFFFFFFF.toInt()
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = px * 0.42f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val baseline = c - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(label, c, baseline, paint)
    return bitmap
}

/**
 * 카카오 지도 위에 퇴근 동선과 강좌 마커를 올린다.
 *
 * 인증 실패(키 해시 미등록)나 네트워크 문제로 지도가 뜨지 않으면 [onFailed]를 부른다.
 * 호출하는 쪽은 그때 자체 도식 지도로 갈아탄다. 시연 중 빈 화면이 뜨지 않게 하기 위한 안전장치다.
 */
@Composable
fun KakaoCourseMap(
    courses: List<Course>,
    settings: Settings,
    selected: Course?,
    onSelect: (Course) -> Unit,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val handlesRef = remember { mutableStateOf<MapHandles?>(null) }

    val groups = remember(courses) { groupByFacility(courses) }
    // 클릭 콜백이 만들어질 때의 선택값에 묶이지 않도록 최신 값을 읽어 간다.
    val selectedIdRef = remember { mutableStateOf<String?>(null) }
    selectedIdRef.value = selected?.courseId

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { view ->
                mapViewRef.value = view
                view.start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {
                            handlesRef.value = null
                        }

                        override fun onMapError(error: Exception?) {
                            Log.e("fitbalance", "카카오 지도 오류", error)
                            onFailed(error?.message ?: "지도를 불러오지 못했습니다")
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            runCatching {
                                handlesRef.value = setUpMap(
                                    map, groups, settings, density,
                                    selectedId = { selectedIdRef.value },
                                    onSelect = onSelect,
                                )
                            }.onFailure {
                                Log.e("fitbalance", "지도 그리기 실패", it)
                                onFailed(it.message ?: "지도를 그리지 못했습니다")
                            }
                        }

                        /** 처음 보여줄 중심점. 직장과 집의 중간. */
                        override fun getPosition(): LatLng = LatLng.from(
                            (settings.workLat + settings.homeLat) / 2,
                            (settings.workLng + settings.homeLng) / 2,
                        )

                        override fun getZoomLevel(): Int = 11
                    },
                )
            }
        },
    )

    // 선택이 바뀌면 마커를 다시 입힌다. 묶인 시설은 선택된 강좌의 순위 숫자로 바뀐다.
    LaunchedEffect(selected, handlesRef.value) {
        val h = handlesRef.value ?: return@LaunchedEffect
        runCatching {
            groups.forEach { g ->
                val active = g.courses.firstOrNull { it.courseId == selected?.courseId }
                val show = active ?: g.courses.first()
                val (normal, on) = h.styles[show.courseId] ?: return@forEach
                h.layer.getLabel(g.labelId)?.changeStyles(if (active != null) on else normal)
            }
        }.onFailure { Log.w("fitbalance", "마커 강조 갱신 실패", it) }
    }

    // 화면이 살아 있는 동안만 지도 렌더링을 돌린다.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef.value?.resume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef.value?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.pause()
        }
    }
}

/** 동선 선과 강좌·직장·집 마커를 올리고, 나중에 갱신할 것들을 돌려준다. */
private fun setUpMap(
    map: KakaoMap,
    groups: List<FacilityGroup>,
    settings: Settings,
    density: Float,
    selectedId: () -> String?,
    onSelect: (Course) -> Unit,
): MapHandles {
    val work = LatLng.from(settings.workLat, settings.workLng)
    val home = LatLng.from(settings.homeLat, settings.homeLng)

    // 퇴근 동선
    map.routeLineManager?.layer?.let { routeLayer ->
        val stylesSet = RouteLineStylesSet.from(
            "commute",
            RouteLineStyles.from(RouteLineStyle.from(14f, 0x8800A874.toInt())),
        )
        routeLayer.addRouteLine(
            RouteLineOptions
                .from(listOf(RouteLineSegment.from(listOf(work, home)).setStyles(stylesSet.getStyles(0))))
                .setStylesSet(stylesSet)
        )
    }

    val labelManager = map.labelManager
        ?: error("labelManager를 가져오지 못했습니다")
    val layer = labelManager.layer
        ?: error("labelLayer를 가져오지 못했습니다")

    // 시설 묶음마다, 그 안의 강좌별로 (기본/선택) 스타일을 만들어 둔다.
    // 어떤 강좌가 선택됐느냐에 따라 마커에 찍히는 순위 숫자가 달라진다.
    val styles = mutableMapOf<String, Pair<LabelStyles, LabelStyles>>()
    groups.forEach { g ->
        g.courses.forEachIndexed { i, c ->
            val rank = g.ranks[i]
            val normal = labelManager.addLabelStyles(
                LabelStyles.from(
                    "m$rank",
                    LabelStyle.from(markerBitmap(density, rank, false, g.courses.size)),
                )
            ) ?: error("마커 스타일 등록 실패")
            val on = labelManager.addLabelStyles(
                LabelStyles.from(
                    "m${rank}_on",
                    LabelStyle.from(markerBitmap(density, rank, true, g.courses.size)),
                )
            ) ?: error("선택 마커 스타일 등록 실패")
            styles[c.courseId] = normal to on
        }
        // 처음에는 대표 강좌(가장 높은 순위)로 표시한다.
        layer.addLabel(
            LabelOptions.from(g.labelId, g.position)
                .setStyles(styles.getValue(g.courses.first().courseId).first)
        )
    }

    labelManager.addLabelStyles(
        LabelStyles.from("work", LabelStyle.from(endpointBitmap(density, "직")))
    )?.let { layer.addLabel(LabelOptions.from("work", work).setStyles(it)) }
    labelManager.addLabelStyles(
        LabelStyles.from("home", LabelStyle.from(endpointBitmap(density, "집")))
    )?.let { layer.addLabel(LabelOptions.from("home", home).setStyles(it)) }

    map.setOnLabelClickListener { _, _, label ->
        val group = groups.firstOrNull { it.labelId == label.labelId }
        if (group != null) {
            // 같은 시설에 강좌가 여러 개면 누를 때마다 다음 강좌로 넘어간다.
            val current = group.courses.indexOfFirst { it.courseId == selectedId() }
            onSelect(group.courses[(current + 1) % group.courses.size])
        }
        true
    }

    // 직장·집과 강좌가 모두 화면에 들어오도록 카메라를 맞춘다.
    val allPoints = (listOf(work, home) + groups.map { it.position }).toTypedArray()
    map.moveCamera(CameraUpdateFactory.fitMapPoints(allPoints, 140))

    return MapHandles(map, layer, styles)
}
