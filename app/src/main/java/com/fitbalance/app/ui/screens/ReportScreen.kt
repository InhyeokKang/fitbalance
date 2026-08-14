package com.fitbalance.app.ui.screens

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitbalance.app.data.DiagnoseResponse
import com.fitbalance.app.ui.UiState
import com.fitbalance.app.ui.components.ErrorBox
import com.fitbalance.app.ui.components.LoadingBox
import com.fitbalance.app.ui.components.PercentileBar
import com.fitbalance.app.ui.components.VSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    state: UiState<DiagnoseResponse>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSeeCourses: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("진단 리포트") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        }
    ) { pad ->
        when (state) {
            is UiState.Loading, UiState.Idle ->
                LoadingBox(Modifier.padding(pad), "체력 기준표와 대조하는 중...")

            is UiState.Error ->
                ErrorBox(state.message, onRetry = onRetry, modifier = Modifier.padding(pad))

            is UiState.Success -> ReportBody(state.data, onSeeCourses, Modifier.padding(pad))
        }
    }
}

@Composable
private fun ReportBody(
    r: DiagnoseResponse,
    onSeeCourses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        VSpace(8)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        r.imbalanceType,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${r.totalScore}점",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                VSpace(8)
                Text(r.imbalanceDesc, style = MaterialTheme.typography.bodyMedium)
                VSpace(8)
                Text(
                    "${if (r.gender == "M") "남성" else "여성"} · ${r.ageBand.replace("s", "대")} 기준",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        VSpace(20)
        Text("체력요인별 백분위", fontWeight = FontWeight.Bold)
        VSpace(4)
        r.factors.forEach { f ->
            PercentileBar(
                label = f.label,
                percentile = f.percentile,
                grade = f.grade,
                highlight = f.factor in r.weakFactors,
            )
        }
        VSpace(8)
        Text(
            "⚠ 표시된 ${r.factors.filter { it.factor in r.weakFactors }.joinToString("·") { it.label }} 이(가) " +
                "가장 뒤처진 두 요인입니다. 추천 강좌는 이 두 가지를 기준으로 고릅니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        VSpace(20)
        HorizontalDivider()
        VSpace(12)
        Text("측정 항목 상세", fontWeight = FontWeight.Bold)
        VSpace(8)
        r.items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(item.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${item.value}${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("100명 중 ${100 - item.percentile}등", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        item.grade,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        VSpace(12)
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("체질량지수(BMI)")
                Text(
                    "${r.bmi.value}  ·  ${r.bmi.category}",
                    fontWeight = FontWeight.Bold,
                    color = if (r.bmi.inNormalRange) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }

        VSpace(24)
        Button(onClick = onSeeCourses, modifier = Modifier.fillMaxWidth()) {
            Text("이 약점에 맞는 강좌 보기")
        }
        VSpace(32)
    }
}
