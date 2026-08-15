package com.fitbalance.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.Facility
import com.fitbalance.app.data.FacilityResponse
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Chip
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

/**
 * 시간표 없이 이용할 수 있는 주변 공공체육시설 목록.
 *
 * 강좌는 요일·시각이 정해져 있어 못 맞추는 사람이 있고, 체력이 이미 다 양호해
 * 강습이 필요 없는 사람도 있다. 그런 경우의 답이 이 화면이다.
 */
@Composable
fun FacilityScreen(
    state: UiState<FacilityResponse>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        when (state) {
            is UiState.Loading, UiState.Idle ->
                LoadingBox(message = "퇴근 동선 안의 체육시설을 찾는 중...", mood = MascotMood.SEARCHING)

            is UiState.Error -> ErrorBox(state.message, onRetry = onRetry)

            is UiState.Success -> {
                val r = state.data
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                ) {
                    item {
                        BrandBar(onSettings)
                        Eyebrow(if (r.profile == "maintain") "지금 수준 유지" else "시간표 없이")
                        VSpace(6)
                        Text("가서 바로 쓰는 곳", style = MaterialTheme.typography.titleLarge)
                        VSpace(12)
                        if (r.notice != null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Brand.MintSoft)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    r.notice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.MintDeep,
                                )
                            }
                            VSpace(14)
                        }
                    }

                    if (r.items.isEmpty()) {
                        item {
                            AppCard {
                                Text(
                                    "동선 주변에서 찾지 못했습니다. 설정에서 최대 거리를 넓혀 보세요.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.Muted,
                                )
                            }
                        }
                    }

                    items(r.items) { f ->
                        FacilityCard(f)
                        VSpace(10)
                    }

                    item {
                        VSpace(6)
                        if (r.source != null) {
                            Text(
                                "출처 ${r.source}",
                                fontSize = 11.sp,
                                color = Brand.Muted2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            VSpace(14)
                        }
                        GhostButton("뒤로", onBack)
                    }
                }
            }
        }
    }
}

@Composable
private fun FacilityCard(f: Facility) {
    AppCard(padding = 18) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        f.facility,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    VSpace(4)
                    Text(f.sport, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    if (f.address != null) {
                        VSpace(2)
                        Text(f.address, style = MaterialTheme.typography.bodySmall, color = Brand.Muted2)
                    }
                }
                if (f.distanceKm != null) {
                    Chip("${f.distanceKm}km")
                }
            }
            if (f.matchReason != null) {
                VSpace(10)
                Text(
                    f.matchReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.MintDeep,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
