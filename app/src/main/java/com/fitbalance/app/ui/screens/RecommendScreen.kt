package com.fitbalance.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.Course
import com.fitbalance.app.data.RecommendResponse
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.Chip
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

private val FACTOR_LABEL = mapOf(
    "strength" to "근력", "flex" to "유연성",
    "cardio" to "심폐지구력", "balance" to "평형성",
)

@Composable
fun RecommendScreen(
    state: UiState<RecommendResponse>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onCourseClick: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        when (state) {
            is UiState.Loading, UiState.Idle ->
                LoadingBox(message = "퇴근 동선 안의 강좌를 찾는 중...")

            is UiState.Error -> ErrorBox(state.message, onRetry = onRetry)

            is UiState.Success -> {
                val r = state.data
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                ) {
                    item {
                        BrandBar(onSettings)
                        Eyebrow("STEP 2")
                        VSpace(6)
                        Text(
                            "퇴근길 맞춤 강좌",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        VSpace(12)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            val weak = r.query.weakFactors.mapNotNull { FACTOR_LABEL[it] }
                            Chip("${weak.joinToString("·")} 보강")
                            Chip("${r.query.leaveTime} 퇴근", bg = Brand.TrackBg, fg = Brand.Muted)
                            Chip(
                                "동선 ${r.query.maxDistanceKm}km 이내",
                                bg = Brand.TrackBg, fg = Brand.Muted,
                            )
                        }
                        VSpace(16)
                    }

                    if (r.total == 0) {
                        item { EmptyResult(r.hint, onSettings, onBack) }
                    } else {
                        itemsIndexed(r.items, key = { _, c -> c.courseId }) { index, course ->
                            CourseCard(course, index + 1) { onCourseClick(course.courseId) }
                        }
                        item {
                            VSpace(14)
                            GhostButton("조건 수정", onSettings)
                            VSpace(9)
                            GhostButton("홈으로", onBack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(c: Course, rank: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brand.Surface)
            .clickable(onClick = onClick)
    ) {
        // 순위 배지. 상위 3개만 강조색.
        Box(
            Modifier
                .size(width = 30.dp, height = 26.dp)
                .background(
                    if (rank <= 3) Brand.PrimaryGradient else SolidColor(Brand.TrackBg),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomEnd = 14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$rank",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (rank <= 3) androidx.compose.ui.graphics.Color(0xFF04120D) else Brand.Muted2,
            )
        }

        Column(Modifier.padding(start = 40.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        c.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    VSpace(5)
                    Text(
                        "${c.facility} · ${c.sport}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted,
                    )
                    c.address?.let {
                        VSpace(2)
                        Text(it, fontSize = 11.5.sp, color = Brand.Muted2)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${((c.score ?: 0.0) * 100).toInt()}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp,
                        color = Brand.MintDeep,
                    )
                    Text("매칭", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Brand.Muted2)
                }
            }

            VSpace(7)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.weekday} ${c.startTime}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    Modifier
                        .padding(horizontal = 7.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Brand.Muted2)
                )
                Text(
                    "동선에서 ${c.distanceKm}km",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            c.matchReason?.let {
                VSpace(10)
                Text(
                    it,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand.MintDeep,
                )
            }
        }
    }
}

@Composable
private fun EmptyResult(hint: String?, onSettings: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(MascotMood.SEARCHING, size = 110)
        VSpace(10)
        Text(
            "조건에 맞는 강좌가 없습니다",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        VSpace(6)
        Text(
            hint ?: "최대 거리를 넓히거나 퇴근 시각을 조정해 보세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.Muted,
            textAlign = TextAlign.Center,
        )
        VSpace(18)
        PrimaryButton("조건 수정하기", onSettings)
        VSpace(9)
        GhostButton("홈으로", onBack)
    }
}
