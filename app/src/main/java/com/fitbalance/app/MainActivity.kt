package com.fitbalance.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import com.kakao.vectormap.KakaoMapSdk
import com.fitbalance.app.ui.AppNav
import com.fitbalance.app.ui.screens.KakaoMapState
import com.fitbalance.app.ui.theme.Brand
import com.fitbalance.app.ui.theme.FitBalanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 카카오 지도 SDK 초기화. 실패해도 앱은 그대로 뜨고 지도만 간이 버전으로 바뀐다.
        if (BuildConfig.KAKAO_MAP_KEY.isBlank()) {
            KakaoMapState.markUnavailable("지도 키가 설정되지 않았습니다 (local.properties의 KAKAO_MAP_KEY)")
        } else {
            runCatching { KakaoMapSdk.init(this, BuildConfig.KAKAO_MAP_KEY) }
                .onSuccess { KakaoMapState.markAvailable() }
                .onFailure {
                    Log.w("fitbalance", "카카오 지도 SDK 초기화 실패, 간이 지도로 대체", it)
                    KakaoMapState.markUnavailable(it)
                }
        }

        setContent {
            FitBalanceTheme {
                // API 35는 앱을 강제로 edge-to-edge로 띄운다.
                // 인셋을 넣지 않으면 상단이 상태바에, 하단 버튼이 내비게이션 바에 가린다.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brand.Bg)
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    AppNav()
                }
            }
        }
    }
}
