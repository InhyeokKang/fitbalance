package com.fitbalance.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitbalance.app.data.ApiClient
import com.fitbalance.app.data.Center
import com.fitbalance.app.data.CenterResponse
import com.fitbalance.app.data.Course
import com.fitbalance.app.data.DiagnoseRequest
import com.fitbalance.app.data.DiagnoseResponse
import com.fitbalance.app.data.FacilityResponse
import com.fitbalance.app.data.HomeDiagnoseRequest
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
    /** 화면에 보여 줄 위치 이름. 좌표만으로는 어디인지 알 수 없다. */
    val workLabel: String = "",
    val homeLabel: String = "",
    val leaveTime: String,
    val maxDistanceKm: Double,
) {
    /** 체력인증센터를 이 지역부터 보여주기 위해 쓴다. 이름 앞부분이 시도다. */
    val workSido: String? get() = workLabel.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    val deviceId: String = prefs.deviceId

    init {
        // 저장된 주소가 있으면 먼저 적용한다. 첫 호출 전에 해야 한다.
        ApiClient.setBaseUrl(prefs.serverUrl)
    }

    /** 설정 화면이 보여 줄, 지금 쓰는 서버 주소. */
    val serverUrl: String get() = ApiClient.currentBaseUrl()

    fun saveServerUrl(url: String) {
        prefs.serverUrl = url
        ApiClient.setBaseUrl(url)
    }

    /** 첫 실행 안내를 띄울지. 한 번 보고 나면 다시 뜨지 않는다. */
    private val _showTutorial = MutableStateFlow(!prefs.tutorialDone)
    val showTutorial: StateFlow<Boolean> = _showTutorial.asStateFlow()

    /** 첫 실행 위치·시각 입력을 띄울지. */
    private val _showOnboarding = MutableStateFlow(!prefs.onboardingDone)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    fun finishTutorial() {
        prefs.tutorialDone = true
        _showTutorial.value = false
    }

    private val _settings = MutableStateFlow(
        Settings(
            workLat = prefs.workLat,
            workLng = prefs.workLng,
            homeLat = prefs.homeLat,
            homeLng = prefs.homeLng,
            workLabel = prefs.workLabel,
            homeLabel = prefs.homeLabel,
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

    private val _centers = MutableStateFlow<UiState<CenterResponse>>(UiState.Idle)
    val centers: StateFlow<UiState<CenterResponse>> = _centers.asStateFlow()

    private val _facilities = MutableStateFlow<UiState<FacilityResponse>>(UiState.Idle)
    val facilities: StateFlow<UiState<FacilityResponse>> = _facilities.asStateFlow()

    /** 최근 진단 결과. 홈 화면 요약과 추천 요청에 쓴다. */
    val lastDiagnosis: DiagnoseResponse?
        get() = (_diagnosis.value as? UiState.Success)?.data

    fun saveSettings(s: Settings) {
        prefs.workLat = s.workLat
        prefs.workLng = s.workLng
        prefs.homeLat = s.homeLat
        prefs.homeLng = s.homeLng
        prefs.workLabel = s.workLabel
        prefs.homeLabel = s.homeLabel
        prefs.leaveTime = s.leaveTime
        prefs.maxDistanceKm = s.maxDistanceKm
        _settings.value = s
    }

    /** 첫 실행 위치·시각 입력을 마쳤을 때. 저장까지 함께 한다. */
    fun finishOnboarding(s: Settings) {
        saveSettings(s)
        prefs.onboardingDone = true
        _showOnboarding.value = false
    }

    fun diagnose(req: DiagnoseRequest) {
        _diagnosis.value = UiState.Loading
        viewModelScope.launch {
            _diagnosis.value = runCatchingApi { ApiClient.service.diagnose(req) }
        }
    }

    /** 집에서 잰 3항목으로 진단한다. 기준표 대조는 정밀 진단과 같다. */
    fun diagnoseHome(req: HomeDiagnoseRequest) {
        _diagnosis.value = UiState.Loading
        viewModelScope.launch {
            _diagnosis.value = runCatchingApi { ApiClient.service.diagnoseHome(req) }
        }
    }

    /** 측정 장비가 없는 사용자를 위한 간편 자가진단. 결과는 진단과 같은 화면에 흐른다. */
    fun selfCheck(req: SelfCheckRequest) {
        _diagnosis.value = UiState.Loading
        viewModelScope.launch {
            _diagnosis.value = runCatchingApi { ApiClient.service.selfCheck(req) }
        }
    }

    /** 진단 결과를 바탕으로 추천한다. */
    fun recommend() {
        val diag = lastDiagnosis
        requestRecommend(
            diagnosisId = diag?.diagnosisId,
            weakFactors = if (diag == null) listOf("flex", "power") else null,
        )
    }

    /**
     * 진단 없이 사용자가 직접 고른 약점으로 추천한다.
     *
     * 체력인증센터에서 측정하면 결과지에 항목별 등급이 이미 나오므로,
     * 그 값을 앱에 다시 입력하게 하지 않고 약한 항목만 받아 바로 추천한다.
     */
    fun recommendFor(weakFactors: List<String>) {
        requestRecommend(diagnosisId = null, weakFactors = weakFactors)
    }

    private fun requestRecommend(diagnosisId: String?, weakFactors: List<String>?) {
        _recommendation.value = UiState.Loading
        viewModelScope.launch {
            _recommendation.value = runCatchingApi {
                ApiClient.service.recommend(recommendBody(diagnosisId, weakFactors))
            }
        }
    }

    /**
     * 강좌 대신 시설을 찾는다.
     *
     * 시간표에 매이기 싫은 사람과, 이미 체력이 다 양호해서 강좌가 필요 없는
     * 사람에게는 '언제든 가서 쓸 수 있는 곳'이 답이다.
     */
    fun loadFacilities() {
        val diag = lastDiagnosis
        _facilities.value = UiState.Loading
        viewModelScope.launch {
            _facilities.value = runCatchingApi {
                ApiClient.service.facilities(
                    recommendBody(
                        diagnosisId = diag?.diagnosisId,
                        weakFactors = if (diag == null) null else diag.weakFactors,
                    )
                )
            }
        }
    }

    private fun recommendBody(diagnosisId: String?, weakFactors: List<String>?): RecommendRequest {
        val s = _settings.value
        return RecommendRequest(
            deviceId = deviceId,
            diagnosisId = diagnosisId,
            weakFactors = weakFactors,
            workLat = s.workLat,
            workLng = s.workLng,
            homeLat = s.homeLat,
            homeLng = s.homeLng,
            leaveTime = s.leaveTime,
            maxDistanceKm = s.maxDistanceKm,
            limit = 10,
        )
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

    /** 설정한 직장 위치의 시도를 앞세워 센터를 불러온다. */
    fun loadCenters() {
        _centers.value = UiState.Loading
        viewModelScope.launch {
            _centers.value = runCatchingApi {
                ApiClient.service.centers(_settings.value.workSido)
            }
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
