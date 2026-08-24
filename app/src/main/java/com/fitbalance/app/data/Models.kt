package com.fitbalance.app.data

import com.google.gson.annotations.SerializedName

// contracts/design.md의 요청/응답 JSON을 그대로 옮긴 모델이다.

data class DiagnoseRequest(
    @SerializedName("device_id") val deviceId: String,
    val gender: String,
    val age: Int,
    @SerializedName("height_cm") val heightCm: Double,
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("grip_kg") val gripKg: Double,
    @SerializedName("sit_up") val sitUp: Int,
    @SerializedName("sit_reach_cm") val sitReachCm: Double,
    @SerializedName("shuttle_run") val shuttleRun: Int,
    @SerializedName("standing_jump_cm") val standingJumpCm: Double,
)

/**
 * 집에서 직접 잰 3항목. 악력계와 20m 구간이 필요한 두 항목은 빠져 있다.
 * 응답은 정밀 진단과 같은 형태이며, 못 잰 요인은 unmeasuredFactors로 온다.
 */
data class HomeDiagnoseRequest(
    @SerializedName("device_id") val deviceId: String,
    val gender: String,
    val age: Int,
    @SerializedName("height_cm") val heightCm: Double,
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("sit_up") val sitUp: Int,
    @SerializedName("sit_reach_cm") val sitReachCm: Double,
    @SerializedName("standing_jump_cm") val standingJumpCm: Double,
)

/** 집에서는 잴 수 없어 센터로 넘긴 요인. */
data class UnmeasuredFactor(
    val factor: String,
    val label: String,
    /** 그 요인을 재는 공단 측정항목 이름. */
    val item: String,
    /** 집에서 왜 못 재는지. */
    val reason: String,
)

/**
 * 도구 없이 답하는 간편 자가진단. 각 문항 0~3점이며 클수록 좋다.
 * 결과는 기준표와 대조한 값이 아니라 추정치다.
 */
data class SelfCheckRequest(
    @SerializedName("device_id") val deviceId: String,
    val gender: String,
    val age: Int,
    val strength: Int,
    val endurance: Int,
    val flex: Int,
    val cardio: Int,
    val power: Int,
    val activity: Int,
)

data class FactorScore(
    val factor: String,
    val label: String,
    val percentile: Int,
    val grade: String,
)

data class ItemScore(
    val item: String,
    val label: String,
    val value: Double,
    val unit: String,
    val percentile: Int,
    val grade: String,
)

data class BmiResult(
    val value: Double,
    val category: String,
    @SerializedName("in_normal_range") val inNormalRange: Boolean,
)

data class DiagnoseResponse(
    @SerializedName("diagnosis_id") val diagnosisId: String,
    @SerializedName("measured_at") val measuredAt: String,
    @SerializedName("age_band") val ageBand: String,
    @SerializedName("age_band_label") val ageBandLabel: String? = null,
    val gender: String,
    /** 간편 자가진단으로 얻은 추정치이면 true. 화면에 "참고용"을 표시한다. */
    val estimated: Boolean = false,
    /** "improve" 약점 보완 / "maintain" 모든 요인이 또래 상위권이라 유지가 목표 */
    val profile: String = "improve",
    /** estimated일 때 함께 보여줄 안내 문구. */
    val notice: String? = null,
    @SerializedName("total_score") val totalScore: Int,
    @SerializedName("imbalance_type") val imbalanceType: String,
    @SerializedName("imbalance_desc") val imbalanceDesc: String,
    val factors: List<FactorScore>,
    @SerializedName("weak_factors") val weakFactors: List<String>,
    val items: List<ItemScore> = emptyList(),
    /** 집 측정에서 못 잰 요인. 정밀 진단이면 비어 있다. */
    @SerializedName("unmeasured_factors") val unmeasuredFactors: List<UnmeasuredFactor> = emptyList(),
    val bmi: BmiResult? = null,
)

data class RecommendRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("diagnosis_id") val diagnosisId: String? = null,
    @SerializedName("weak_factors") val weakFactors: List<String>? = null,
    @SerializedName("work_lat") val workLat: Double,
    @SerializedName("work_lng") val workLng: Double,
    @SerializedName("home_lat") val homeLat: Double,
    @SerializedName("home_lng") val homeLng: Double,
    @SerializedName("leave_time") val leaveTime: String,
    @SerializedName("max_distance_km") val maxDistanceKm: Double,
    val limit: Int = 10,
)

data class CourseTags(
    val strength: Int,
    val endurance: Int,
    val flex: Int,
    val cardio: Int,
    val power: Int,
)

data class Course(
    @SerializedName("course_id") val courseId: String,
    val title: String,
    val facility: String,
    /** 시설 주소. 참조표에 없으면 null이며, 이때 화면은 시설명만 보여준다. */
    val address: String? = null,
    val sport: String,
    val weekday: String,
    @SerializedName("start_time") val startTime: String,
    val lat: Double,
    val lng: Double,
    val tags: CourseTags,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    val score: Double? = null,
    @SerializedName("match_reason") val matchReason: String? = null,
    @SerializedName("apply_url") val applyUrl: String? = null,
)

data class RecommendQuery(
    @SerializedName("weak_factors") val weakFactors: List<String>,
    @SerializedName("leave_time") val leaveTime: String,
    @SerializedName("max_distance_km") val maxDistanceKm: Double,
)

data class RecommendResponse(
    val query: RecommendQuery,
    /** "improve" 약점 보완 / "maintain" 이미 다 양호해서 유지가 목표 */
    val profile: String = "improve",
    @SerializedName("profile_notice") val profileNotice: String? = null,
    val total: Int,
    val items: List<Course>,
    val hint: String? = null,
    /** 강좌가 0건일 때 동선 주변에 있는 공공체육시설 수. 대안을 제시하는 데 쓴다. */
    @SerializedName("facility_count") val facilityCount: Int = 0,
    /** 이 강좌 목록이 어떤 데이터인지 밝히는 안내. 개설 여부 확인을 당부한다. */
    @SerializedName("data_notice") val dataNotice: String? = null,
)

/** 시간표 없이 언제든 이용할 수 있는 공공체육시설. */
data class Facility(
    val facility: String,
    val address: String? = null,
    val sport: String,
    val lat: Double,
    val lng: Double,
    val tags: CourseTags,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    val score: Double? = null,
    @SerializedName("match_reason") val matchReason: String? = null,
)

/** 주소 입력창에서 고르는 지역. 좌표는 그 동네 공공체육시설들의 중심점이다. */
data class Place(
    val label: String,
    val sido: String,
    val sigungu: String,
    val dong: String? = null,
    val lat: Double,
    val lng: Double,
)

data class PlaceResponse(
    val query: String,
    val total: Int,
    val items: List<Place>,
)

data class FacilityResponse(
    val profile: String = "improve",
    val notice: String? = null,
    val source: String? = null,
    val total: Int,
    val items: List<Facility>,
)

/** 국민체력100 체력인증센터. 전국 무료이며, 앱은 여기서 정밀 측정을 안내한다. */
data class Center(
    @SerializedName("center_code") val centerCode: String,
    val sido: String,
    val sigungu: String,
    @SerializedName("center_name") val centerName: String,
    val address: String,
    val tel: String,
    /** 지도 앱으로 넘길 검색어. */
    @SerializedName("map_query") val mapQuery: String,
)

data class CenterResponse(
    val total: Int,
    @SerializedName("nearby_count") val nearbyCount: Int,
    val sido: String?,
    @SerializedName("reserve_url") val reserveUrl: String,
    val notice: String,
    val items: List<Center>,
)
