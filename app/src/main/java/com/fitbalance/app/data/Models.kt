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
    @SerializedName("one_leg_stand_sec") val oneLegStandSec: Double,
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
    val gender: String,
    @SerializedName("total_score") val totalScore: Int,
    @SerializedName("imbalance_type") val imbalanceType: String,
    @SerializedName("imbalance_desc") val imbalanceDesc: String,
    val factors: List<FactorScore>,
    @SerializedName("weak_factors") val weakFactors: List<String>,
    val items: List<ItemScore>,
    val bmi: BmiResult,
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
    val flex: Int,
    val cardio: Int,
    val balance: Int,
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
    val total: Int,
    val items: List<Course>,
    val hint: String? = null,
)
