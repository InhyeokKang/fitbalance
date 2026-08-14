package com.fitbalance.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import com.fitbalance.app.ui.AppNav
import com.fitbalance.app.ui.theme.Brand
import com.fitbalance.app.ui.theme.FitBalanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
