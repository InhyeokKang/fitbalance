package com.fitbalance.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.fitbalance.app.data.DiagnoseResponse
import com.fitbalance.app.ui.components.PercentileBar
import com.fitbalance.app.ui.components.VSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    lastDiagnosis: DiagnoseResponse?,
    onMeasure: () -> Unit,
    onRecommend: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("핏밸런스") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                },
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp)
        ) {
            Text(
                "앉아서 일하는 몸,\n어디가 무너졌는지부터 봅니다",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            VSpace(8)
            Text(
                "국민체력100 기준표로 내 체력을 진단하고, 퇴근길 동선 안의 공공 체육 강좌를 추천받으세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(24)

            if (lastDiagnosis == null) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("아직 진단 기록이 없습니다", fontWeight = FontWeight.Bold)
                        VSpace(6)
                        Text(
                            "체력 측정값 6가지를 입력하면 약 5초 만에 불균형 유형이 나옵니다.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("최근 진단", style = MaterialTheme.typography.labelMedium)
                        VSpace(4)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                lastDiagnosis.imbalanceType,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${lastDiagnosis.totalScore}점",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        VSpace(12)
                        lastDiagnosis.factors.forEach { f ->
                            PercentileBar(
                                label = f.label,
                                percentile = f.percentile,
                                grade = f.grade,
                                highlight = f.factor in lastDiagnosis.weakFactors,
                            )
                        }
                    }
                }
            }

            VSpace(24)
            Button(onClick = onMeasure, modifier = Modifier.fillMaxWidth()) {
                Text(if (lastDiagnosis == null) "체력 측정하기" else "다시 측정하기")
            }
            VSpace(10)
            OutlinedButton(
                onClick = onRecommend,
                modifier = Modifier.fillMaxWidth(),
                enabled = lastDiagnosis != null,
            ) {
                Text("추천 강좌 보기")
            }
            if (lastDiagnosis == null) {
                VSpace(6)
                Text(
                    "진단을 먼저 마쳐야 추천을 받을 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
