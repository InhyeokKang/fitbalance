package com.fitbalance.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.fitbalance.app.data.Center
import com.fitbalance.app.data.CenterResponse
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Chip
import com.fitbalance.app.ui.components.DarkButton
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.HSpace
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

/**
 * 처음 가는 사람이 실제로 막히는 지점만 모았다.
 *
 * 센터 방문의 장벽은 비용이 아니라 "뭘 알고 가야 하는지 모른다"는 쪽이다.
 * 공단 안내에 흩어져 있는 것을 순서대로 정리한다.
 */
private val FIRST_VISIT = listOf(
    "예약" to "홈페이지나 전화로 예약합니다. 당일 방문은 받지 않는 곳이 많습니다.",
    "준비물" to "신분증만 있으면 됩니다. 비용은 없습니다.",
    "복장" to "운동복과 운동화. 달리기와 뛰기가 있어 정장 구두로는 못 잽니다.",
    "식사" to "측정 2시간 전부터는 먹지 않습니다. 카페인도 피하세요.",
    "걸리는 시간" to "문진과 상담까지 1시간~1시간 30분 정도 걸립니다.",
    "받는 것" to "항목별 등급이 적힌 결과지와 운동처방 상담. 인증 기준을 넘으면 인증서도 나옵니다.",
)

@Composable
private fun FirstVisitCard() {
    AppCard(padding = 18) {
        Column {
            Text("처음 가시나요", style = MaterialTheme.typography.titleMedium)
            VSpace(4)
            Text(
                "만 13세 이상이면 누구나, 무료입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Muted2,
            )
            VSpace(12)
            FIRST_VISIT.forEach { (label, desc) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Brand.MintDeep,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 체력인증센터 찾기.
 *
 * 측정 장비가 없어도 전국 체력인증센터에서 무료로 잴 수 있다는 사실을 알리는 화면이다.
 * 좌표 데이터가 없어 거리순 정렬은 하지 않고, 설정한 직장 지역(시도)을 앞에 둔다.
 */
@Composable
fun CenterScreen(
    state: UiState<CenterResponse>,
    /** 집 측정 결과가 있으면 센터에 가져갈 요약으로 넘어갈 수 있다. */
    hasDiagnosis: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onBrief: () -> Unit,
) {
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        when (state) {
            is UiState.Loading, UiState.Idle ->
                LoadingBox(message = "체력인증센터를 불러오는 중...", mood = MascotMood.SEARCHING)

            is UiState.Error -> ErrorBox(state.message, onRetry = onRetry)

            is UiState.Success -> {
                val r = state.data
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                ) {
                    item {
                        BrandBar()
                        Eyebrow("무료 체력측정")
                        VSpace(6)
                        Text("체력인증센터 찾기", style = MaterialTheme.typography.titleLarge)
                        VSpace(12)

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brand.MintSoft)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Mascot(MascotMood.WORKOUT, size = 60, bob = false)
                            HSpace(8)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "전국 ${r.total}개소 · 무료",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Brand.MintDeep,
                                )
                                VSpace(3)
                                Text(
                                    r.notice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.MintDeep,
                                )
                            }
                        }

                        VSpace(12)
                        DarkButton("예약 페이지 열기", onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, r.reserveUrl.toUri()))
                        })
                        VSpace(6)
                        Text(
                            "국민체력100 공식 예약 페이지로 이동합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.Muted2,
                        )

                        VSpace(18)
                        FirstVisitCard()

                        if (hasDiagnosis) {
                            VSpace(12)
                            GhostButton("센터에 가져갈 요약 보기", onBrief)
                            VSpace(6)
                            Text(
                                "집에서 잰 값을 정리해 보여 줍니다. 상담이 그만큼 빨라집니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Brand.Muted2,
                            )
                        }

                        if (r.nearbyCount > 0 && r.sido != null) {
                            VSpace(16)
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Chip("${r.sido} ${r.nearbyCount}개소 먼저")
                            }
                        }
                        VSpace(14)
                    }

                    items(r.items, key = { it.centerCode }) { center ->
                        CenterCard(center, isNearby = center.sido == r.sido) {
                            // 지도 앱이 없으면 브라우저 지도로 열린다.
                            val query = java.net.URLEncoder.encode(center.mapQuery, "UTF-8")
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://map.kakao.com/?q=$query".toUri())
                            )
                        }
                    }

                    item {
                        VSpace(14)
                        GhostButton("뒤로", onBack)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterCard(center: Center, isNearby: Boolean, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.padding(bottom = 10.dp).clickable(onClick = onClick),
        padding = 16,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    center.centerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                HSpace(6)
                Text(
                    "${center.sido} ${center.sigungu}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isNearby) Brand.MintDeep else Brand.Muted2,
                )
            }
            VSpace(5)
            Text(
                center.address,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Muted,
            )
            if (center.tel.isNotBlank()) {
                VSpace(5)
                Text(
                    center.tel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            VSpace(8)
            Text(
                "지도에서 보기",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Brand.MintDeep,
            )
        }
    }
}
