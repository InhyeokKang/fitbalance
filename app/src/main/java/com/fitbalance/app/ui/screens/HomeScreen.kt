package com.fitbalance.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.DiagnoseResponse
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Chip
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.HSpace
import com.fitbalance.app.ui.components.HeroCard
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.Wordmark
import com.fitbalance.app.ui.components.moodOfScore
import com.fitbalance.app.ui.components.RadarChart
import com.fitbalance.app.ui.components.ScoreRing
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

@Composable
fun HomeScreen(
    lastDiagnosis: DiagnoseResponse?,
    onMeasure: () -> Unit,
    onSelfCheck: () -> Unit,
    onRecommend: () -> Unit,
    onReport: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BrandBar(onSettings)

        if (lastDiagnosis == null) EmptyHome(onMeasure, onSelfCheck)
        else DiagnosedHome(lastDiagnosis, onRecommend, onReport, onMeasure)

        VSpace(40)
    }
}

/** 상단 브랜드 줄. 모든 주요 화면이 같은 형태를 쓴다. */
@Composable
fun BrandBar(onSettings: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Brand.PrimaryGradient)
            )
            HSpace(7)
            Wordmark()
        }
        if (onSettings != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brand.Surface)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "설정",
                    tint = Brand.Muted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(onMeasure: () -> Unit, onSelfCheck: () -> Unit) {
    Text(
        "앉아서 일하는 몸,\n어디가 무너졌는지\n부터 봅니다",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
    )
    Text(
        "국민체력100 기준표로 체력을 진단하고,\n퇴근길 동선 안의 공공 체육 강좌를 추천받으세요.",
        style = MaterialTheme.typography.bodyMedium,
        color = Brand.Muted,
    )
    VSpace(22)

    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "STEP 1",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.52f),
                )
                VSpace(6)
                Text(
                    "3분이면 끝납니다",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                VSpace(8)
                Text(
                    "국민체력100 기준으로\n어디가 무너졌는지 짚어 드립니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
                VSpace(14)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        "근력 · 근지구력 · 유연성 · 심폐 · 순발력",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
            Mascot(MascotMood.WORKOUT, size = 104, bodyColor = Brand.Mint)
        }
    }

    VSpace(22)
    PrimaryButton("측정값 넣고 정밀 진단", onMeasure)
    VSpace(6)
    Text(
        "무료 체력인증센터(전국 75개소)에서 받은 결과지의 숫자를 넣습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = Brand.Muted2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        textAlign = TextAlign.Center,
    )

    VSpace(16)
    GhostButton("측정값이 없어요 · 간편 자가진단 1분", onSelfCheck)
    VSpace(6)
    Text(
        "도구 없이 6문항으로 약점을 추정합니다.",
        style = MaterialTheme.typography.bodySmall,
        color = Brand.Muted2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DiagnosedHome(
    d: DiagnoseResponse,
    onRecommend: () -> Unit,
    onReport: () -> Unit,
    onMeasure: () -> Unit,
) {
    HeroCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(d.totalScore)
                Column(Modifier.padding(start = 18.dp)) {
                    Text(
                        "내 체력 종합",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.52f),
                    )
                    VSpace(3)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${d.totalScore}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.2).sp,
                            color = Color.White,
                        )
                        Text(
                            "점",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(
                        "${if (d.gender == "M") "남성" else "여성"} · ${d.ageBand.replace("s", "대")} 기준",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
            VSpace(16)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        d.imbalanceType,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    VSpace(8)
                    Text(
                        d.imbalanceDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
                HSpace(6)
                Mascot(moodOfScore(d.totalScore), size = 84, bodyColor = Brand.Mint)
            }
        }
    }

    VSpace(14)
    AppCard(padding = 18) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            RadarChart(d.factors, d.weakFactors)
            VSpace(6)
            val weakNames = d.factors.filter { it.factor in d.weakFactors }
                .joinToString("·") { it.label }
            Chip("약점 $weakNames", bg = Brand.CoralSoft, fg = Brand.Coral)
        }
    }

    VSpace(22)
    PrimaryButton("이 약점에 맞는 강좌 보기", onRecommend)
    VSpace(9)
    GhostButton("진단 리포트 다시 보기", onReport)
    VSpace(9)
    GhostButton("다시 측정하기", onMeasure)
}
