package com.fitbalance.app.ui.screens

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.kakao.vectormap.route.RouteLineStylesSet
import com.fitbalance.app.R

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
    // 마커 id로 강좌를 되찾기 위한 표. 라벨 클릭 콜백은 id만 준다.
    val byLabelId = remember(courses) { courses.associateBy { "course_${it.courseId}" } }

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { view ->
                mapViewRef.value = view
                view.start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() = Unit
                        override fun onMapError(error: Exception?) {
                            Log.e("fitbalance", "카카오 지도 오류", error)
                            onFailed(error?.message ?: "지도를 불러오지 못했습니다")
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            runCatching {
                                drawCommute(map, courses, settings, selected, byLabelId, onSelect)
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
        update = { /* 선택 강조는 아래 라벨 스타일 갱신으로 처리한다 */ },
    )

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

/** 동선 선과 강좌 마커를 올린다. */
private fun drawCommute(
    map: KakaoMap,
    courses: List<Course>,
    settings: Settings,
    selected: Course?,
    byLabelId: Map<String, Course>,
    onSelect: (Course) -> Unit,
) {
    val work = LatLng.from(settings.workLat, settings.workLng)
    val home = LatLng.from(settings.homeLat, settings.homeLng)

    // 퇴근 동선
    val routeLayer = map.routeLineManager?.layer
    if (routeLayer != null) {
        val stylesSet = RouteLineStylesSet.from(
            "commute",
            RouteLineStyles.from(RouteLineStyle.from(14f, 0x8800A874.toInt())),
        )
        routeLayer.addRouteLine(
            RouteLineOptions.from(
                listOf(RouteLineSegment.from(listOf(work, home)).setStyles(stylesSet.getStyles(0)))
            ).setStylesSet(stylesSet)
        )
    }

    // 마커
    val labelLayer = map.labelManager?.layer ?: return
    val pinStyles: LabelStyles = map.labelManager!!.addLabelStyles(
        LabelStyles.from(LabelStyle.from(R.drawable.marker_course))
    )!!
    val pinStylesSelected: LabelStyles = map.labelManager!!.addLabelStyles(
        LabelStyles.from(LabelStyle.from(R.drawable.marker_course_selected))
    )!!
    val endpointStyles: LabelStyles = map.labelManager!!.addLabelStyles(
        LabelStyles.from(LabelStyle.from(R.drawable.marker_endpoint))
    )!!

    courses.forEach { c ->
        val styles = if (c.courseId == selected?.courseId) pinStylesSelected else pinStyles
        labelLayer.addLabel(
            LabelOptions.from("course_${c.courseId}", LatLng.from(c.lat, c.lng))
                .setStyles(styles)
        )
    }
    labelLayer.addLabel(LabelOptions.from("work", work).setStyles(endpointStyles))
    labelLayer.addLabel(LabelOptions.from("home", home).setStyles(endpointStyles))

    map.setOnLabelClickListener { _, _, label ->
        byLabelId[label.labelId]?.let(onSelect)
        true
    }

    // 직장·집이 모두 보이도록 카메라를 맞춘다.
    map.moveCamera(
        CameraUpdateFactory.fitMapPoints(arrayOf(work, home), 120)
    )
}
