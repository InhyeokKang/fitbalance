package com.fitbalance.app.data

import android.content.Context
import java.util.UUID

/** 로그인 없이 기기 로컬 UUID로 사용자를 식별하고, 추천 조건을 저장한다. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fitbalance", Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            val saved = sp.getString("device_id", null)
            if (saved != null) return saved
            val created = UUID.randomUUID().toString()
            sp.edit().putString("device_id", created).apply()
            return created
        }

    var workLat: Double
        get() = sp.getFloat("work_lat", 37.5665f).toDouble()
        set(v) = sp.edit().putFloat("work_lat", v.toFloat()).apply()

    var workLng: Double
        get() = sp.getFloat("work_lng", 126.9780f).toDouble()
        set(v) = sp.edit().putFloat("work_lng", v.toFloat()).apply()

    var homeLat: Double
        get() = sp.getFloat("home_lat", 37.4979f).toDouble()
        set(v) = sp.edit().putFloat("home_lat", v.toFloat()).apply()

    var homeLng: Double
        get() = sp.getFloat("home_lng", 127.0276f).toDouble()
        set(v) = sp.edit().putFloat("home_lng", v.toFloat()).apply()

    var leaveTime: String
        get() = sp.getString("leave_time", "18:30") ?: "18:30"
        set(v) = sp.edit().putString("leave_time", v).apply()

    var maxDistanceKm: Double
        get() = sp.getFloat("max_distance_km", 3.0f).toDouble()
        set(v) = sp.edit().putFloat("max_distance_km", v.toFloat()).apply()
}

/**
 * 설정 화면에서 고를 수 있는 위치 프리셋.
 * 지도 화면이 이번 범위 밖이라 좌표 대신 지역을 골라 쓴다.
 * 좌표는 내부 계산용이고, 화면에는 [address]만 보여준다.
 */
data class PlacePreset(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)

val PLACE_PRESETS = listOf(
    PlacePreset("시청·광화문", "서울특별시 중구 세종대로 일대", 37.5665, 126.9780),
    PlacePreset("강남역", "서울특별시 강남구 강남대로 일대", 37.4979, 127.0276),
    PlacePreset("여의도", "서울특별시 영등포구 여의도동 일대", 37.5219, 126.9245),
    PlacePreset("판교", "경기도 성남시 분당구 판교역로 일대", 37.3947, 127.1112),
    PlacePreset("홍대·마포", "서울특별시 마포구 양화로 일대", 37.5563, 126.9236),
    PlacePreset("잠실", "서울특별시 송파구 올림픽로 일대", 37.5133, 127.1000),
    PlacePreset("성수·왕십리", "서울특별시 성동구 왕십리로 일대", 37.5445, 127.0557),
)
