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

    /**
     * 화면에 보여 줄 위치 이름. 좌표만으로는 어디를 골랐는지 알 수 없다.
     * 비어 있으면 아직 고르지 않은 것이다.
     */
    var workLabel: String
        get() = sp.getString("work_label", "") ?: ""
        set(v) = sp.edit().putString("work_label", v).apply()

    var homeLabel: String
        get() = sp.getString("home_label", "") ?: ""
        set(v) = sp.edit().putString("home_label", v).apply()

    /** 첫 실행 안내를 이미 봤는지. 한 번 본 뒤에는 재시작해도 다시 뜨지 않는다. */
    var tutorialDone: Boolean
        get() = sp.getBoolean("tutorial_done", false)
        set(v) = sp.edit().putBoolean("tutorial_done", v).apply()

    /** 첫 실행 때 위치·시각을 받아 두었는지. */
    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit().putBoolean("onboarding_done", v).apply()

    /**
     * 사용자가 직접 지정한 서버 주소. 비어 있으면 빌드에 박힌 기본값을 쓴다.
     *
     * APK만 받아 자기 폰에 깐 사람은 기본값(에뮬레이터용 10.0.2.2)으로 못 붙는다.
     * 같은 와이파이의 노트북 주소나 배포한 서버 주소를 여기에 넣는다.
     */
    var serverUrl: String
        get() = sp.getString("server_url", "") ?: ""
        set(v) = sp.edit().putString("server_url", v.trim()).apply()
}
