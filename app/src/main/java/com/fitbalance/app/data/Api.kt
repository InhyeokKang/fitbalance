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
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("api/v1/diagnose")
    suspend fun diagnose(@Body body: DiagnoseRequest): DiagnoseResponse

    /** 측정 장비가 없는 사용자를 위한 간편 자가진단. 응답 형태는 진단과 같다. */
    @POST("api/v1/selfcheck")
    suspend fun selfCheck(@Body body: SelfCheckRequest): DiagnoseResponse

    @POST("api/v1/recommend")
    suspend fun recommend(@Body body: RecommendRequest): RecommendResponse

    @GET("api/v1/courses/{courseId}")
    suspend fun course(@Path("courseId") courseId: String): Course
}

object ApiClient {
    // 서버 주소는 BuildConfig(local.properties)에서 온다.
    val service: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
