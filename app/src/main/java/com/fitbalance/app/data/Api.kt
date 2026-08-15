package com.fitbalance.app.data

import com.fitbalance.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("api/v1/diagnose")
    suspend fun diagnose(@Body body: DiagnoseRequest): DiagnoseResponse

    /** 집에서 잴 수 있는 3항목만으로 하는 진단. 응답 형태는 정밀 진단과 같다. */
    @POST("api/v1/diagnose/home")
    suspend fun diagnoseHome(@Body body: HomeDiagnoseRequest): DiagnoseResponse

    /** 측정 장비가 없는 사용자를 위한 간편 자가진단. 응답 형태는 진단과 같다. */
    @POST("api/v1/selfcheck")
    suspend fun selfCheck(@Body body: SelfCheckRequest): DiagnoseResponse

    /** 시간표 없이 이용할 수 있는 주변 공공체육시설. 요청 형태는 강좌 추천과 같다. */
    @POST("api/v1/facilities")
    suspend fun facilities(@Body body: RecommendRequest): FacilityResponse

    /** 집·직장 위치를 찾기 위한 지역 검색. q가 비면 시도별 대표 지역을 준다. */
    @GET("api/v1/places")
    suspend fun places(@Query("q") q: String? = null): PlaceResponse

    /** 체력인증센터 목록. sido를 주면 그 지역을 앞으로 정렬해 준다. */
    @GET("api/v1/centers")
    suspend fun centers(@Query("sido") sido: String? = null): CenterResponse

    @POST("api/v1/recommend")
    suspend fun recommend(@Body body: RecommendRequest): RecommendResponse

    @GET("api/v1/courses/{courseId}")
    suspend fun course(@Path("courseId") courseId: String): Course
}

object ApiClient {
    /**
     * 기본 서버 주소는 BuildConfig(local.properties)에서 온다.
     *
     * 다만 APK만 받아 자기 폰에 깐 사람은 그 주소(에뮬레이터용 10.0.2.2)로는 못 붙는다.
     * 그래서 설정 화면에서 주소를 바꿀 수 있게 하고, 바뀌면 여기서 다시 만든다.
     */
    private var baseUrl: String = BuildConfig.BASE_URL
    private var cached: ApiService? = null

    val service: ApiService
        @Synchronized get() = cached ?: build().also { cached = it }

    /** 설정에서 서버 주소를 바꿨을 때. 빈 값이면 빌드 기본값으로 되돌린다. */
    @Synchronized
    fun setBaseUrl(url: String) {
        val normalized = url.trim().ifBlank { BuildConfig.BASE_URL }
            .let { if (it.endsWith("/")) it else "$it/" }
        if (normalized == baseUrl) return
        baseUrl = normalized
        cached = null
    }

    /** 지금 쓰는 주소. 설정 화면이 보여 준다. */
    fun currentBaseUrl(): String = baseUrl

    private fun build(): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
