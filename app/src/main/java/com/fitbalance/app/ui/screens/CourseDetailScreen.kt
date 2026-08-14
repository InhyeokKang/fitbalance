package com.fitbalance.app.ui.screens

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.fitbalance.app.data.Course
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Chip
import com.fitbalance.app.ui.components.DarkButton
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.HeroCard
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.SectionHeader
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

@Composable
fun CourseDetailScreen(
    state: UiState<Course>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        when (state) {
            is UiState.Loading, UiState.Idle ->
                LoadingBox(message = "강좌 정보를 가져오는 중...", mood = MascotMood.SEARCHING)
            is UiState.Error -> ErrorBox(state.message, onRetry)
            is UiState.Success -> {
                val c = state.data
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    BrandBar()

                    HeroCard {
                        Column {
                            Text(
                                c.facility,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.52f),
                            )
                            VSpace(6)
                            Text(
                                c.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                            )
                            c.address?.let {
                                VSpace(6)
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.62f),
                                )
                            }
                            VSpace(14)
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .padding(horizontal = 11.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "${c.weekday}요일 ${c.startTime} 시작 · ${c.sport}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    SectionHeader("이 강좌가 키우는 체력요인")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (c.tags.strength == 1) Chip("근력")
                        if (c.tags.flex == 1) Chip("유연성")
                        if (c.tags.cardio == 1) Chip("심폐지구력")
                        if (c.tags.balance == 1) Chip("평형성")
                    }

                    VSpace(18)
                    AppCard {
                        Column {
                            DetailRow("일정", "${c.weekday}요일 ${c.startTime}")
                            HorizontalDivider(color = Brand.Line)
                            DetailRow("시설", c.facility)
                            // 좌표는 사용자에게 의미가 없어 보여주지 않는다.
                            // 주소가 아직 참조표에 없는 시설이면 이 줄을 통째로 생략한다.
                            c.address?.let {
                                HorizontalDivider(color = Brand.Line)
                                DetailRow("주소", it)
                            }
                            HorizontalDivider(color = Brand.Line)
                            DetailRow("종목", c.sport)
                            c.distanceKm?.let {
                                HorizontalDivider(color = Brand.Line)
                                DetailRow("퇴근 동선에서", "${it}km")
                            }
                            c.score?.let {
                                HorizontalDivider(color = Brand.Line)
                                DetailRow("매칭 점수", "${(it * 100).toInt()}점")
                            }
                        }
                    }

                    c.matchReason?.let {
                        SectionHeader("추천 이유")
                        AppCard {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Brand.MintDeep,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    VSpace(26)
                    DarkButton(
                        "신청 페이지 열기",
                        onClick = {
                            val url = c.applyUrl ?: return@DarkButton
                            // 예약 연동은 범위 밖. 외부 브라우저로 신청 페이지만 연다.
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        },
                        enabled = !c.applyUrl.isNullOrBlank(),
                    )
                    VSpace(9)
                    Text(
                        "앱 안에서 예약되지 않고, 해당 시설의 신청 페이지로 이동합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VSpace(14)
                    GhostButton("목록으로", onBack)
                    VSpace(40)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
