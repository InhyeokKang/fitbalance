package com.fitbalance.app.ui.screens

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fitbalance.app.R
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

/** 위도 1도의 거리(m). 겹친 마커를 몇 미터 밀어낼 때 쓴다. */
private const val METERS_PER_DEG = 111_320.0

/** 같은 좌표에 겹친 마커를 이 반경(m)만큼 원형으로 벌린다. */
private const val SPREAD_METERS = 35.0

private fun labelIdOf(course: Course) = "course_${course.courseId}"

/**
 * 같은 시설에서 열리는 강좌는 좌표가 완전히 같아 지도에서 하나만 보인다.
 * 표시용으로만 몇 미터씩 원형으로 벌린다. 거리 계산에는 쓰지 않는다.
 */
private fun spreadDuplicates(courses: List<Course>): Map<String, LatLng> {
    val result = mutableMapOf<String, LatLng>()
    courses
        .groupBy { "%.5f,%.5f".format(it.lat, it.lng) }
        .forEach { (_, group) ->
            if (group.size == 1) {
                val c = group.first()
                result[labelIdOf(c)] = LatLng.from(c.lat, c.lng)
            } else {
                group.forEachIndexed { i, c ->
                    val angle = 2.0 * PI * i / group.size
                    val dLat = SPREAD_METERS * sin(angle) / METERS_PER_DEG
                    val dLng = SPREAD_METERS * cos(angle) /
                        (METERS_PER_DEG * cos(c.lat * PI / 180.0))
                    result[labelIdOf(c)] = LatLng.from(c.lat + dLat, c.lng + dLng)
                }
            }
        }
    return result
}

/** 지도에 올린 것들을 나중에 갱신하려고 들고 있는 묶음. */
private class MapHandles(
    val map: KakaoMap,
    val layer: LabelLayer,
    val normal: LabelStyles,
    val selected: LabelStyles,
)

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
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val handlesRef = remember { mutableStateOf<MapHandles?>(null) }

    // 라벨 id -> 강좌. 라벨 클릭 콜백은 id만 준다.
    val byLabelId = remember(courses) { courses.associateBy { labelIdOf(it) } }
    val positions = remember(courses) { spreadDuplicates(courses) }

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
                                handlesRef.value =
                                    setUpMap(map, courses, positions, settings, byLabelId, onSelect)
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

    // 선택이 바뀌면 마커 스타일을 다시 입힌다. 이게 없으면 지도 위 강조가 그대로 남는다.
    LaunchedEffect(selected, handlesRef.value) {
        val h = handlesRef.value ?: return@LaunchedEffect
        runCatching {
            courses.forEach { c ->
                val styles = if (c.courseId == selected?.courseId) h.selected else h.normal
                h.layer.getLabel(labelIdOf(c))?.changeStyles(styles)
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
    courses: List<Course>,
    positions: Map<String, LatLng>,
    settings: Settings,
    byLabelId: Map<String, Course>,
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

    val normal = labelManager.addLabelStyles(
        LabelStyles.from("course", LabelStyle.from(R.drawable.marker_course))
    ) ?: error("마커 스타일 등록 실패")
    val selectedStyles = labelManager.addLabelStyles(
        LabelStyles.from("course_on", LabelStyle.from(R.drawable.marker_course_selected))
    ) ?: error("선택 마커 스타일 등록 실패")
    val endpoint = labelManager.addLabelStyles(
        LabelStyles.from("endpoint", LabelStyle.from(R.drawable.marker_endpoint))
    ) ?: error("직장·집 마커 스타일 등록 실패")

    courses.forEach { c ->
        val id = labelIdOf(c)
        layer.addLabel(
            LabelOptions.from(id, positions[id] ?: LatLng.from(c.lat, c.lng)).setStyles(normal)
        )
    }
    layer.addLabel(LabelOptions.from("work", work).setStyles(endpoint))
    layer.addLabel(LabelOptions.from("home", home).setStyles(endpoint))

    map.setOnLabelClickListener { _, _, label ->
        byLabelId[label.labelId]?.let(onSelect)
        true
    }

    // 직장·집이 모두 보이도록 카메라를 맞춘다.
    map.moveCamera(CameraUpdateFactory.fitMapPoints(arrayOf(work, home), 120))

    return MapHandles(map, layer, normal, selectedStyles)
}
