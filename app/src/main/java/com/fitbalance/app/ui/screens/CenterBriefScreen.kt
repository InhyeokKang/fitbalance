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
import com.fitbalance.app.data.RecommendResponse
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Chip
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.HSpace
import com.fitbalance.app.ui.components.HeroCard
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.SectionHeader
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

/**
 * 센터에 가져가는 요약 한 장.
 *
 * 우리는 센터와 실제로 연동돼 있지 않다. 대신 사용자가 이 화면을 그대로 보여 주면
 * 운동처방사가 문진에서 물어볼 것들이 이미 정리돼 있게 만든다.
 * 집에서 잰 값, 아직 모르는 항목, 하려는 운동 — 상담에 필요한 세 가지다.
 */
@Composable
fun CenterBriefScreen(
    diagnosis: DiagnoseResponse?,
    recommendation: UiState<RecommendResponse>,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BrandBar()
        Eyebrow("센터에 보여 주세요")
        VSpace(6)
        Text("측정 전 요약", style = MaterialTheme.typography.titleLarge)
        VSpace(14)

        if (diagnosis == null) {
            AppCard {
                Text(
                    "아직 측정 결과가 없습니다. 집에서 먼저 재고 오시면 여기에 정리해 드립니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Muted,
                )
            }
            VSpace(20)
            GhostButton("뒤로", onBack)
            VSpace(40)
            return
        }

        HeroCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${if (diagnosis.gender == "M") "남성" else "여성"} · " +
                            "${diagnosis.ageBandLabel ?: diagnosis.ageBand}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.52f),
                    )
                    VSpace(6)
                    Text(
                        diagnosis.imbalanceType,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    VSpace(8)
                    Text(
                        if (diagnosis.estimated)
                            "설문으로 추정한 결과입니다. 실제 측정으로 확인이 필요합니다."
                        else
                            "국민체력100 기준표와 대조한 결과입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
                HSpace(6)
                Mascot(MascotMood.SEARCHING, size = 76, bodyColor = Brand.Mint)
            }
        }

        if (diagnosis.items.isNotEmpty()) {
            SectionHeader("집에서 잰 값")
            AppCard {
                Column {
                    diagnosis.items.forEach { i ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    i.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                VSpace(2)
                                Text(
                                    "${i.value}${i.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.Muted2,
                                )
                            }
                            Text(
                                // 화면 표기는 백분위가 아니라 등수다(100 - 백분위). 작을수록 좋다.
                                "100명 중 ${100 - i.percentile}등",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Brand.Muted,
                            )
                        }
                    }
                    VSpace(8)
                    Text(
                        "측정 방법은 공단 기준을 따랐지만 집에서 잰 값이라 오차가 있을 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted2,
                    )
                }
            }
        }

        if (diagnosis.unmeasuredFactors.isNotEmpty()) {
            SectionHeader("센터에서 재야 하는 항목")
            AppCard {
                Column {
                    diagnosis.unmeasuredFactors.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                u.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                u.item,
                                style = MaterialTheme.typography.bodySmall,
                                color = Brand.Muted,
                            )
                        }
                    }
                }
            }
        }

        val picked = (recommendation as? UiState.Success)?.data?.items.orEmpty().take(3)
        if (picked.isNotEmpty()) {
            SectionHeader("하려는 운동")
            AppCard {
                Column {
                    picked.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${c.sport} · ${c.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                VSpace(2)
                                Text(
                                    "${c.facility} · ${c.weekday} ${c.startTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.Muted2,
                                )
                            }
                        }
                    }
                    VSpace(8)
                    Text(
                        "이 운동을 해도 되는지, 강도는 어느 정도가 좋은지 물어보세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted2,
                    )
                }
            }
        }

        SectionHeader("물어보면 좋은 것")
        AppCard {
            Column {
                listOf(
                    "집에서 잰 값이 실제 측정과 얼마나 차이 나나요?",
                    "제 결과에서 가장 먼저 손봐야 할 곳은 어디인가요?",
                    "이 운동을 주 몇 회, 어느 강도로 하면 되나요?",
                    "다음 측정은 언제쯤 다시 받는 게 좋을까요?",
                ).forEach {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("·", color = Brand.MintDeep, fontWeight = FontWeight.Bold)
                        HSpace(8)
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.Muted,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        VSpace(16)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brand.MintSoft)
                .padding(14.dp)
        ) {
            Text(
                "이 화면을 그대로 보여 주시면 됩니다. 캡처해 두면 통신이 안 되는 곳에서도 쓸 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.MintDeep,
            )
        }

        VSpace(20)
        GhostButton("뒤로", onBack)
        VSpace(40)
    }
}
