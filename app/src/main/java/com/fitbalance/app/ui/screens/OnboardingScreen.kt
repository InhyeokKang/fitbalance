package com.fitbalance.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.Place
import com.fitbalance.app.ui.Settings
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.HSpace
import com.fitbalance.app.ui.components.HeroCard
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.PlacePicker
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.TimeField
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.components.Wordmark
import com.fitbalance.app.ui.theme.Brand

/** 고를 수 있는 최대 거리. 이보다 멀면 "퇴근길에 들른다"고 하기 어렵다. */
private val DISTANCE_CHOICES = listOf(1.0, 2.0, 3.0, 5.0, 10.0)

/**
 * 첫 실행 때 추천 조건을 한 번에 받는 화면.
 *
 * 이걸 먼저 받아 두면 진단을 마치자마자 바로 맞는 강좌가 나온다.
 * 나중에 바꾸고 싶으면 홈 오른쪽 위 톱니바퀴에서 같은 항목을 고칠 수 있다.
 */
@Composable
fun OnboardingScreen(
    current: Settings,
    onDone: (Settings) -> Unit,
) {
    var work by remember { mutableStateOf<Place?>(null) }
    var home by remember { mutableStateOf<Place?>(null) }
    var leaveTime by remember { mutableStateOf(current.leaveTime) }
    var distance by remember { mutableStateOf(current.maxDistanceKm) }

    val timeValid = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(leaveTime)
    val ready = work != null && home != null && timeValid
    val filled = listOf(work != null, home != null, timeValid).count { it }
    val progress by animateFloatAsState(filled / 3f, tween(400), label = "onboardProgress")

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        VSpace(20)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(Brand.PrimaryGradient))
            HSpace(7)
            Wordmark()
        }

        VSpace(18)
        HeroCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "시작하기 전에",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.52f),
                    )
                    VSpace(6)
                    Text(
                        "어디로 오가시나요",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    VSpace(8)
                    Text(
                        "직장과 집을 알면 그 사이 동선에서\n갈 만한 곳만 골라 드립니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
                Mascot(MascotMood.SEARCHING, size = 92, bodyColor = Brand.Mint)
            }
        }

        VSpace(16)
        ProgressBar(progress, filled)

        VSpace(16)
        AppCard(padding = 18) {
            PlacePicker(
                label = "직장 위치",
                hint = "회사가 있는 동네를 검색하세요",
                selected = work,
                onPick = { work = it },
            )
        }

        VSpace(12)
        AppCard(padding = 18) {
            PlacePicker(
                label = "집 위치",
                hint = "사는 동네를 검색하세요",
                selected = home,
                onPick = { home = it },
            )
        }

        VSpace(12)
        AppCard(padding = 18) {
            Column {
                TimeField(leaveTime, timeValid) { leaveTime = it }
                VSpace(18)
                Text(
                    "퇴근 동선에서 얼마나 벗어나도 괜찮나요",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand.Muted,
                )
                VSpace(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DISTANCE_CHOICES.forEach { km ->
                        val on = km == distance
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Brand.Ink else Brand.TrackBg)
                                .clickable { distance = km }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${km.toInt()}km",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) Color.White else Brand.Muted,
                            )
                        }
                    }
                }
            }
        }

        VSpace(22)
        PrimaryButton(
            text = if (ready) "시작하기" else "직장·집을 골라 주세요",
            onClick = {
                val w = work ?: return@PrimaryButton
                val h = home ?: return@PrimaryButton
                onDone(
                    current.copy(
                        workLat = w.lat, workLng = w.lng, workLabel = w.label,
                        homeLat = h.lat, homeLng = h.lng, homeLabel = h.label,
                        leaveTime = leaveTime,
                        maxDistanceKm = distance,
                    )
                )
            },
            enabled = ready,
        )
        VSpace(8)
        Text(
            "나중에 홈 오른쪽 위 톱니바퀴에서 바꿀 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.Muted2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        VSpace(40)
    }
}

@Composable
private fun ProgressBar(progress: Float, filled: Int) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Eyebrow("$filled / 3 입력")
            Text(
                if (filled == 3) "다 됐습니다" else "세 가지만 받습니다",
                fontSize = 11.sp,
                color = Brand.Muted2,
            )
        }
        VSpace(7)
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brand.TrackBg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brand.PrimaryGradient)
            )
        }
    }
}
