package com.fitbalance.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.fitbalance.app.data.Course
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.InfoChip
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.VSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    state: UiState<Course>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("강좌 상세") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        }
    ) { pad ->
        when (state) {
            is UiState.Loading, UiState.Idle -> LoadingBox(Modifier.padding(pad))
            is UiState.Error -> ErrorBox(state.message, onRetry, Modifier.padding(pad))
            is UiState.Success -> {
                val c = state.data
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(pad)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        c.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    VSpace(6)
                    Text(
                        "${c.facility} · ${c.sport}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    VSpace(20)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DetailRow("일정", "${c.weekday}요일 ${c.startTime}")
                            DetailRow("시설", c.facility)
                            DetailRow("좌표", "${c.lat}, ${c.lng}")
                            c.distanceKm?.let { DetailRow("퇴근 동선에서", "${it}km") }
                            c.score?.let { DetailRow("매칭 점수", "${(it * 100).toInt()}점") }
                        }
                    }

                    VSpace(20)
                    Text("이 강좌가 키우는 체력요인", fontWeight = FontWeight.Bold)
                    VSpace(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (c.tags.strength == 1) InfoChip("근력")
                        if (c.tags.flex == 1) InfoChip("유연성")
                        if (c.tags.cardio == 1) InfoChip("심폐지구력")
                        if (c.tags.balance == 1) InfoChip("평형성")
                    }

                    c.matchReason?.let {
                        VSpace(16)
                        HorizontalDivider()
                        VSpace(12)
                        Text("추천 이유", fontWeight = FontWeight.Bold)
                        VSpace(4)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }

                    VSpace(28)
                    Button(
                        onClick = {
                            val url = c.applyUrl ?: return@Button
                            // 예약 연동은 범위 밖. 외부 브라우저로 신청 페이지만 연다.
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        },
                        enabled = !c.applyUrl.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("신청 페이지 열기")
                    }
                    VSpace(8)
                    Text(
                        "앱 안에서 예약되지 않고, 해당 시설의 신청 페이지로 이동합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VSpace(32)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
