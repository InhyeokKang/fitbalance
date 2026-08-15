package com.fitbalance.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitbalance.app.data.ApiClient
import com.fitbalance.app.data.Course
import com.fitbalance.app.data.DiagnoseRequest
import com.fitbalance.app.data.DiagnoseResponse
import com.fitbalance.app.data.Prefs
import com.fitbalance.app.data.RecommendRequest
import com.fitbalance.app.data.RecommendResponse
import com.fitbalance.app.data.SelfCheckRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** 화면이 공통으로 쓰는 로딩/성공/실패 3상태. */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** 설정 화면에서 조절하는 추천 조건. */
data class Settings(
    val workLat: Double,
    val workLng: Double,
    val homeLat: Double,
    val homeLng: Double,
    val leaveTime: String,
    val maxDistanceKm: Double,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    val deviceId: String = prefs.deviceId

    private val _settings = MutableStateFlow(
        Settings(
            workLat = prefs.workLat,
            workLng = prefs.workLng,
            homeLat = prefs.homeLat,
            homeLng = prefs.homeLng,
            leaveTime = prefs.leaveTime,
            maxDistanceKm = prefs.maxDistanceKm,
        )
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _diagnosis = MutableStateFlow<UiState<DiagnoseResponse>>(UiState.Idle)
    val diagnosis: StateFlow<UiState<DiagnoseResponse>> = _diagnosis.asStateFlow()

    private val _recommendation = MutableStateFlow<UiState<RecommendResponse>>(UiState.Idle)
    val recommendation: StateFlow<UiState<RecommendResponse>> = _recommendation.asStateFlow()

    private val _courseDetail = MutableStateFlow<UiState<Course>>(UiState.Idle)
    val courseDetail: StateFlow<UiState<Course>> = _courseDetail.asStateFlow()

    /** 최근 진단 결과. 홈 화면 요약과 추천 요청에 쓴다. */
    val lastDiagnosis: DiagnoseResponse?
        get() = (_diagnosis.value as? UiState.Success)?.data

    fun saveSettings(s: Settings) {
        prefs.workLat = s.workLat
        prefs.workLng = s.workLng
        prefs.homeLat = s.homeLat
        prefs.homeLng = s.homeLng
        prefs.leaveTime = s.leaveTime
        prefs.maxDistanceKm = s.maxDistanceKm
        _settings.value = s
    }

    fun diagnose(req: DiagnoseRequest) {
        _diagnosis.value = UiState.Loading
        viewModelScope.launch {
            _diagnosis.value = runCatchingApi { ApiClient.service.diagnose(req) }
        }
    }

    /** 측정 장비가 없는 사용자를 위한 간편 자가진단. 결과는 진단과 같은 화면에 흐른다. */
    fun selfCheck(req: SelfCheckRequest) {
        _diagnosis.value = UiState.Loading
        viewModelScope.launch {
            _diagnosis.value = runCatchingApi { ApiClient.service.selfCheck(req) }
        }
    }

    fun recommend() {
        val diag = lastDiagnosis
        val s = _settings.value
        _recommendation.value = UiState.Loading
        viewModelScope.launch {
            _recommendation.value = runCatchingApi {
                ApiClient.service.recommend(
                    RecommendRequest(
                        deviceId = deviceId,
                        diagnosisId = diag?.diagnosisId,
                        weakFactors = if (diag == null) listOf("flex", "balance") else null,
                        workLat = s.workLat,
                        workLng = s.workLng,
                        homeLat = s.homeLat,
                        homeLng = s.homeLng,
                        leaveTime = s.leaveTime,
                        maxDistanceKm = s.maxDistanceKm,
                        limit = 10,
                    )
                )
            }
        }
    }

    fun loadCourse(courseId: String) {
        _courseDetail.value = UiState.Loading
        viewModelScope.launch {
            val loaded = runCatchingApi { ApiClient.service.course(courseId) }
            // 상세 API는 강좌 원본만 준다. 매칭 점수·거리·추천 이유는 추천 응답에만 있으므로
            // 방금 본 목록에서 찾아 합쳐 준다. 목록을 거치지 않고 열었다면 그대로 둔다.
            _courseDetail.value = if (loaded is UiState.Success) {
                val fromList = (_recommendation.value as? UiState.Success)
                    ?.data?.items?.firstOrNull { it.courseId == courseId }
                UiState.Success(
                    loaded.data.copy(
                        distanceKm = loaded.data.distanceKm ?: fromList?.distanceKm,
                        score = loaded.data.score ?: fromList?.score,
                        matchReason = loaded.data.matchReason ?: fromList?.matchReason,
                    )
                )
            } else loaded
        }
    }

    fun resetDiagnosis() {
        _diagnosis.value = UiState.Idle
        _recommendation.value = UiState.Idle
    }

    private suspend fun <T> runCatchingApi(block: suspend () -> T): UiState<T> = try {
        UiState.Success(block())
    } catch (e: IOException) {
        UiState.Error("서버에 연결하지 못했습니다. 서버 주소와 네트워크를 확인해 주세요.")
    } catch (e: HttpException) {
        val detail = e.response()?.errorBody()?.string().orEmpty()
        UiState.Error("요청이 거부되었습니다 (HTTP ${e.code()}). ${detail.take(200)}")
    } catch (e: Exception) {
        UiState.Error("알 수 없는 오류: ${e.message ?: e::class.java.simpleName}")
    }
}
