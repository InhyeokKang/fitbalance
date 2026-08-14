package com.fitbalance.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.fitbalance.app.ui.AppNav
import com.fitbalance.app.ui.theme.Brand
import com.fitbalance.app.ui.theme.FitBalanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitBalanceTheme {
                Box(Modifier.fillMaxSize().background(Brand.Bg)) {
                    AppNav()
                }
            }
        }
    }
}
