package com.fitbalance.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.DiagnoseResponse
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.FactorBar
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.HeroCard
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.moodOfScore
import com.fitbalance.app.ui.components.RadarChart
import com.fitbalance.app.ui.components.ScoreRing
import com.fitbalance.app.ui.components.SectionHeader
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

@Composable
fun ReportScreen(
    state: UiState<DiagnoseResponse>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSeeCourses: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        when (state) {
            is UiState.Loading, UiState.Idle ->
                LoadingBox(message = "체력 기준표와 대조하는 중...", mood = MascotMood.WORKOUT)
            is UiState.Error -> ErrorBox(state.message, onRetry = onRetry)
            is UiState.Success -> ReportBody(state.data, onSeeCourses, onBack)
        }
    }
}

@Composable
private fun ReportBody(
    r: DiagnoseResponse,
    onSeeCourses: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BrandBar()
        Eyebrow("진단 리포트")
        VSpace(8)

        HeroCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(r.totalScore)
                    Column(Modifier.padding(start = 18.dp)) {
                        Text(
                            "종합 점수",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.52f),
                        )
                        VSpace(3)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${r.totalScore}",
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
                            // 집 측정은 3개 요인만 잰다. 잰 개수를 그대로 쓴다.
                            "${r.factors.size}개 요인 백분위 평균",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
                VSpace(16)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.imbalanceType,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        VSpace(8)
                        Text(
                            r.imbalanceDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                    Mascot(moodOfScore(r.totalScore), size = 84, bodyColor = Brand.Mint)
                }
                VSpace(14)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    val who = "${if (r.gender == "M") "남성" else "여성"} · ${r.ageBandLabel ?: r.ageBand}"
                    Text(
                        if (r.estimated) "$who · 설문 기반 추정" else "$who 기준표와 대조",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // 추정치이거나 못 잰 항목이 있으면 결과를 보기 전에 분명히 알린다.
        val notice = r.notice
        if (notice != null) {
            VSpace(12)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.MintSoft)
                    .padding(14.dp)
            ) {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.MintDeep,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // 모든 요인이 또래 상위권이면 그중 낮은 쪽을 '약점'이라 부르지 않는다.
        val maintain = r.profile == "maintain"

        VSpace(12)
        AppCard(padding = 18) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) { RadarChart(r.factors, if (maintain) emptyList() else r.weakFactors) }
        }

        SectionHeader("체력요인별 순위")
        AppCard {
            Column {
                r.factors.forEach { f ->
                    FactorBar(f.label, f.percentile, f.grade, !maintain && f.factor in r.weakFactors)
                }
                VSpace(12)
                val weakNames = r.factors.filter { it.factor in r.weakFactors }
                    .joinToString("·") { it.label }
                Text(
                    (if (r.estimated)
                        "설문 답변으로 추정한 순위입니다. 숫자가 작을수록 좋습니다.\n"
                    else
                        "같은 성별·나이대 100명과 비교한 순위입니다. 숫자가 작을수록 좋습니다.\n") +
                        (if (maintain)
                            "모든 요인이 상위권이라 따로 보완할 곳이 없습니다. " +
                                "추천은 지금 수준을 이어 가는 쪽으로 고릅니다."
                        else
                            "$weakNames 이(가) 가장 뒤처진 두 요인이며, 추천 강좌는 이 둘을 기준으로 고릅니다."),
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Muted2,
                )
            }
        }

        // 집에서 못 잰 요인. 순위표에 안 나오는 이유를 밝히고 센터로 잇는다.
        if (r.unmeasuredFactors.isNotEmpty()) {
            SectionHeader("아직 모르는 항목")
            AppCard {
                Column {
                    r.unmeasuredFactors.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    u.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                VSpace(2)
                                Text(u.item, fontSize = 11.sp, color = Brand.Muted2)
                            }
                            Text(
                                u.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = Brand.Muted,
                            )
                        }
                    }
                    VSpace(8)
                    Text(
                        "체력인증센터에서 무료로 잽니다. 오늘 잰 3가지를 가져가면 " +
                            "상담이 그만큼 빨라집니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted2,
                    )
                }
            }
        }

        if (r.items.isNotEmpty()) {
        SectionHeader("측정 항목 상세")
        AppCard {
            Column {
                r.items.forEachIndexed { index, item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${item.value}${item.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Brand.Muted2,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "100명 중 ${100 - item.percentile}등",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                item.grade,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Brand.tone(item.percentile),
                            )
                        }
                    }
                    if (index != r.items.lastIndex) HorizontalDivider(color = Brand.Line)
                }
            }
        }
        }

        // BMI는 키·몸무게를 받은 정밀 진단에만 있다.
        r.bmi?.let { bmi ->
            SectionHeader("체질량지수")
            AppCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("BMI", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (bmi.inNormalRange) "정상 구간입니다" else "정상 구간(18.5~23)을 벗어났습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.Muted2,
                        )
                    }
                    val color = if (bmi.inNormalRange) Brand.MintDeep else Brand.Coral
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${bmi.value}",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.7).sp,
                            color = color,
                        )
                        Text(
                            bmi.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                        )
                    }
                }
            }
        }

        VSpace(24)
        PrimaryButton("이 약점에 맞는 강좌 보기", onSeeCourses)
        VSpace(9)
        GhostButton("홈으로", onBack)
        VSpace(40)
    }
}
