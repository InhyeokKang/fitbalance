package com.fitbalance.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitbalance.app.BuildConfig
import com.fitbalance.app.data.PLACE_PRESETS
import com.fitbalance.app.ui.Settings
import com.fitbalance.app.ui.components.HSpace
import com.fitbalance.app.ui.components.VSpace
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: Settings,
    deviceId: String,
    onBack: () -> Unit,
    onSave: (Settings) -> Unit,
) {
    var workLat by remember { mutableStateOf(current.workLat) }
    var workLng by remember { mutableStateOf(current.workLng) }
    var homeLat by remember { mutableStateOf(current.homeLat) }
    var homeLng by remember { mutableStateOf(current.homeLng) }
    var leaveTime by remember { mutableStateOf(current.leaveTime) }
    var maxKm by remember { mutableFloatStateOf(current.maxDistanceKm.toFloat()) }

    val timeValid = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matches(leaveTime)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            VSpace(8)
            Text("직장 위치", fontWeight = FontWeight.Bold)
            VSpace(6)
            PresetRow(workLat, workLng) { p -> workLat = p.lat; workLng = p.lng }
            VSpace(4)
            Text(
                "선택된 좌표: ${"%.4f".format(workLat)}, ${"%.4f".format(workLng)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VSpace(20)
            Text("집 위치", fontWeight = FontWeight.Bold)
            VSpace(6)
            PresetRow(homeLat, homeLng) { p -> homeLat = p.lat; homeLng = p.lng }
            VSpace(4)
            Text(
                "선택된 좌표: ${"%.4f".format(homeLat)}, ${"%.4f".format(homeLng)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VSpace(20)
            OutlinedTextField(
                value = leaveTime,
                onValueChange = { leaveTime = it },
                label = { Text("퇴근 시각 (HH:MM)") },
                isError = !timeValid,
                supportingText = {
                    Text(if (timeValid) "이 시각 30분 뒤부터 시작하는 강좌를 찾습니다" else "00:00~23:59 형식으로 입력해 주세요")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            VSpace(20)
            Text("퇴근 동선에서 최대 ${"%.1f".format(maxKm)}km", fontWeight = FontWeight.Bold)
            Slider(
                value = maxKm,
                onValueChange = { maxKm = (it * 2).roundToInt() / 2f },
                valueRange = 0.5f..10f,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "직장과 집을 잇는 직선에서 이 거리 안에 있는 강좌만 추천합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VSpace(24)
            Button(
                onClick = {
                    onSave(
                        Settings(
                            workLat = workLat,
                            workLng = workLng,
                            homeLat = homeLat,
                            homeLng = homeLng,
                            leaveTime = leaveTime,
                            maxDistanceKm = maxKm.toDouble(),
                        )
                    )
                },
                enabled = timeValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("저장")
            }

            VSpace(28)
            HorizontalDivider()
            VSpace(16)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("기기 정보", fontWeight = FontWeight.Bold)
                    VSpace(8)
                    Text("기기 UUID", style = MaterialTheme.typography.bodySmall)
                    Text(deviceId, style = MaterialTheme.typography.bodySmall)
                    VSpace(8)
                    Text("서버 주소", style = MaterialTheme.typography.bodySmall)
                    Text(BuildConfig.BASE_URL, style = MaterialTheme.typography.bodySmall)
                }
            }
            VSpace(32)
        }
    }
}

@Composable
private fun PresetRow(
    lat: Double,
    lng: Double,
    onPick: (com.fitbalance.app.data.PlacePreset) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PLACE_PRESETS.forEach { p ->
            val selected = abs(p.lat - lat) < 0.0005 && abs(p.lng - lng) < 0.0005
            FilterChip(
                selected = selected,
                onClick = { onPick(p) },
                label = { Text(p.name) },
            )
        }
        HSpace(4)
    }
}
