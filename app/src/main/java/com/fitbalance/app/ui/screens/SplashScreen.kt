package com.fitbalance.app.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.components.Wordmark
import com.fitbalance.app.ui.theme.Brand
import kotlinx.coroutines.delay

/** 시작 화면이 머무는 시간. 길면 앱이 느려 보인다. */
private const val HOLD_MS = 1100L

/**
 * 앱을 열 때 잠깐 보여 주는 시작 화면.
 *
 * 로딩할 것이 있어서 두는 게 아니라, 창 배경에서 홈으로 넘어가는 사이가
 * 비어 보이지 않게 하려는 것이다. 그래서 짧게만 머문다.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.82f,
        animationSpec = tween(520, easing = LinearOutSlowInEasing),
        label = "splashScale",
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(420),
        label = "splashFade",
    )

    LaunchedEffect(Unit) {
        shown = true
        delay(HOLD_MS)
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(Brand.Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(scale).alpha(fade)) {
                Mascot(MascotMood.WORKOUT, size = 132, bobMs = 900, bobAmount = 0.05f)
            }
            VSpace(18)
            Box(Modifier.alpha(fade)) { Wordmark(fontSize = 26) }
            VSpace(10)
            Text(
                "앉아서 일하는 몸을 위한\n공공 체육 안내",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Muted2,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(fade),
            )
        }

        Text(
            "국민체력100 · 공공체육시설 공공데이터 기반",
            fontSize = 11.sp,
            color = Brand.Muted2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .alpha(fade * 0.9f)
                .padding(bottom = 28.dp),
        )
    }
}
