package com.fitbalance.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitbalance.app.data.RecommendResponse
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.InfoChip
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.VSpace

private val FACTOR_LABEL = mapOf(
    "strength" to "근력",
    "flex" to "유연성",
    "cardio" to "심폐지구력",
    "balance" to "평형성",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(
    state: UiState<RecommendResponse>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onCourseClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("추천 강좌") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "조건 수정")
                    }
                },
            )
        }
    ) { pad ->
        when (state) {
            is UiState.Loading, UiState.Idle ->
                LoadingBox(Modifier.padding(pad), "퇴근 동선 안의 강좌를 찾는 중...")

            is UiState.Error ->
                ErrorBox(state.message, onRetry = onRetry, modifier = Modifier.padding(pad))

            is UiState.Success -> {
                val r = state.data
                Column(Modifier.fillMaxSize().padding(pad)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val weak = r.query.weakFactors.mapNotNull { FACTOR_LABEL[it] }
                        InfoChip(weak.joinToString("·"))
                        InfoChip("${r.query.leaveTime} 퇴근")
                        InfoChip("동선 ${r.query.maxDistanceKm.toInt()}km 이내")
                    }

                    if (r.total == 0) {
                        Box(
                            Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "조건에 맞는 강좌가 없습니다",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                VSpace(8)
                                Text(
                                    r.hint ?: "최대 거리를 넓히거나 퇴근 시각을 조정해 보세요.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                VSpace(16)
                                OutlinedButton(onClick = onSettings) { Text("조건 수정하기") }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, bottom = 24.dp
                            )
                        ) {
                            items(r.items, key = { it.courseId }) { c ->
                                Card(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable { onCourseClick(c.courseId) }
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Text(
                                                c.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                "${((c.score ?: 0.0) * 100).toInt()}점",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        VSpace(4)
                                        Text(
                                            "${c.facility} · ${c.sport}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        VSpace(2)
                                        Text(
                                            "${c.weekday}요일 ${c.startTime} 시작" +
                                                (c.distanceKm?.let { "  ·  동선에서 ${it}km" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        c.matchReason?.let {
                                            VSpace(6)
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
