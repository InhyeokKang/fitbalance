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
import com.fitbalance.app.ui.components.tutorialTarget
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

/** 첫 실행 안내가 비출 홈 화면 요소들. */
const val TUTORIAL_HOME_MEASURE = "home_measure"
const val TUTORIAL_SELFCHECK = "selfcheck"
const val TUTORIAL_CENTER = "center"
const val TUTORIAL_SETTINGS = "settings"

@Composable
fun HomeScreen(
    lastDiagnosis: DiagnoseResponse?,
    onHomeMeasure: () -> Unit,
    onMeasure: () -> Unit,
    onSelfCheck: () -> Unit,
    onPickWeak: () -> Unit,
    onFindCenter: () -> Unit,
    onFacilities: () -> Unit,
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

        if (lastDiagnosis == null)
            EmptyHome(onHomeMeasure, onMeasure, onSelfCheck, onPickWeak, onFindCenter)
        else DiagnosedHome(
            lastDiagnosis, onRecommend, onReport, onHomeMeasure, onFindCenter, onFacilities
        )

        VSpace(40)
    }
}

/** 본문 안에 두는 텍스트 링크. 홈에서 센터 찾기로 가는 통로가 여러 곳에 필요하다. */
@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = Brand.MintDeep,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        textAlign = TextAlign.Center,
    )
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
                    .tutorialTarget(TUTORIAL_SETTINGS)
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
private fun EmptyHome(
    onHomeMeasure: () -> Unit,
    onMeasure: () -> Unit,
    onSelfCheck: () -> Unit,
    onPickWeak: () -> Unit,
    onFindCenter: () -> Unit,
) {
    Text(
        "앉아서 일하는 몸,\n어디가 무너졌는지\n부터 봅니다",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
    )
    Text(
        "국민체력100 실측 자료와 대조해 약한 곳을 찾고,\n퇴근길 동선 안의 공공 체육 강좌로 이어 드립니다.",
        style = MaterialTheme.typography.bodyMedium,
        color = Brand.Muted,
    )
    VSpace(22)

    // 트랙 1 — 집에서. 장비 없이 시작할 수 있는 쪽을 앞에 둔다.
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "트랙 1 · 집에서",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.52f),
                )
                VSpace(6)
                Text(
                    "줄자랑 초시계면\n됩니다",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                VSpace(8)
                Text(
                    "국민체력100 측정항목 3가지를 집에서 재고\n또래 순위와 맞는 강좌를 받습니다.",
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
                        "근지구력 · 유연성 · 순발력",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
            Mascot(MascotMood.WORKOUT, size = 104, bodyColor = Brand.Mint)
        }
    }

    VSpace(16)
    Box(Modifier.tutorialTarget(TUTORIAL_HOME_MEASURE)) {
        PrimaryButton("집에서 재고 시작하기", onHomeMeasure)
    }
    VSpace(6)
    Hint("장비 없이 5분. 추정이 아니라 실제 기준표와 대조합니다.")

    VSpace(10)
    Box(Modifier.tutorialTarget(TUTORIAL_SELFCHECK)) {
        GhostButton("잴 여건이 안 돼요 · 6문항 자가진단", onSelfCheck)
    }

    // 트랙 2 — 센터에서. 집에서 못 재는 근력·심폐를 채우는 쪽.
    VSpace(28)
    Box(Modifier.tutorialTarget(TUTORIAL_CENTER)) {
        TrackTwoCard(onFindCenter)
    }

    VSpace(12)
    GhostButton("센터 측정값 넣고 정밀 진단", onMeasure)
    VSpace(6)
    Hint("5개 항목을 모두 넣으면 불균형 유형까지 나옵니다.")

    VSpace(10)
    GhostButton("결과지 있어요 · 약한 항목만 고르기", onPickWeak)
    VSpace(6)
    Hint("입력 없이 3초. 바로 강좌 추천으로 갑니다.")
}

/** 센터 트랙 안내 카드. 집에서 못 재는 항목이 있다는 사실이 이 카드의 근거다. */
@Composable
private fun TrackTwoCard(onFindCenter: () -> Unit) {
    AppCard(padding = 18) {
        Column {
            Text(
                "트랙 2 · 센터에서",
                style = MaterialTheme.typography.labelSmall,
                color = Brand.MintDeep,
            )
            VSpace(6)
            Text("근력과 심폐지구력은 여기서", style = MaterialTheme.typography.titleMedium)
            VSpace(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "악력계와 20m 구간이 있어야 하는 두 항목은 집에서 잴 수 없습니다. " +
                            "전국 78개 체력인증센터에서 무료로 재고 운동처방까지 받을 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted,
                    )
                }
                HSpace(8)
                Mascot(MascotMood.SEARCHING, size = 64, bodyColor = Brand.Mint)
            }
            VSpace(12)
            TextLink("가까운 체력인증센터 찾기", onFindCenter)
        }
    }
}

/** 버튼 아래 한 줄 설명. */
@Composable
private fun Hint(text: String) {
    Text(
        text,
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
    onHomeMeasure: () -> Unit,
    onFindCenter: () -> Unit,
    onFacilities: () -> Unit,
) {
    // 모든 요인이 또래 상위권이면 '약점'이라 부르지 않는다. 목표가 보완이 아니라 유지다.
    val maintain = d.profile == "maintain"

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
            // 유지형은 붉은 약점 표시를 하지 않는다. 뒤처진 곳이 없기 때문이다.
            RadarChart(d.factors, if (maintain) emptyList() else d.weakFactors)
            VSpace(6)
            if (maintain) {
                Chip("모든 요인 또래 상위권")
            } else {
                val weakNames = d.factors.filter { it.factor in d.weakFactors }
                    .joinToString("·") { it.label }
                Chip("약점 $weakNames", bg = Brand.CoralSoft, fg = Brand.Coral)
            }
        }
    }

    VSpace(22)
    if (maintain) {
        // 강습보다 '언제든 갈 수 있는 곳'이 먼저다. 배울 게 아니라 이어 갈 상황이다.
        PrimaryButton("가까운 체육시설 보기", onFacilities)
        VSpace(6)
        Hint("시간표 없이 이용하는 공공시설입니다.")
        VSpace(9)
        GhostButton("지금 수준 유지에 맞는 강좌 보기", onRecommend)
    } else {
        PrimaryButton("이 약점에 맞는 강좌 보기", onRecommend)
        VSpace(9)
        GhostButton("시간표 없이 · 가까운 체육시설 보기", onFacilities)
    }
    VSpace(9)
    GhostButton("진단 리포트 다시 보기", onReport)
    VSpace(9)
    GhostButton("다시 측정하기", onHomeMeasure)

    // 못 잰 요인이 남아 있으면 그게 센터에 갈 이유다. 그 사실을 이름으로 밝힌다.
    VSpace(20)
    AppCard(padding = 18) {
        Column {
            Text("트랙 2 · 센터에서", style = MaterialTheme.typography.labelSmall, color = Brand.MintDeep)
            VSpace(6)
            val missing = d.unmeasuredFactors.joinToString("·") { it.label }
            Text(
                when {
                    d.estimated -> "설문으로 추정한 결과입니다"
                    missing.isNotEmpty() -> "${missing}은 아직 모릅니다"
                    else -> "다음 측정 때 비교해 드립니다"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            VSpace(8)
            Text(
                when {
                    d.estimated -> "센터에서 실제로 재면 이 결과가 정확해집니다. 무료입니다."
                    missing.isNotEmpty() ->
                        "집에서는 잴 수 없는 항목입니다. 전국 78개 체력인증센터에서 " +
                            "무료로 재고 운동처방까지 받을 수 있습니다."
                    else -> "체력인증센터 측정은 무료이며 언제든 다시 받을 수 있습니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Muted,
            )
            VSpace(12)
            TextLink("가까운 체력인증센터 찾기", onFindCenter)
        }
    }
}
