package com.fitbalance.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.BuildConfig
import com.fitbalance.app.data.PLACE_PRESETS
import com.fitbalance.app.data.PlacePreset
import com.fitbalance.app.ui.Settings
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    current: Settings,
    deviceId: String,
    onBack: () -> Unit,
    onSave: (Settings) -> Unit,
) {
    var workLat by remember { mutableDoubleStateOf(current.workLat) }
    var workLng by remember { mutableDoubleStateOf(current.workLng) }
    var homeLat by remember { mutableDoubleStateOf(current.homeLat) }
    var homeLng by remember { mutableDoubleStateOf(current.homeLng) }
    var leaveTime by remember { mutableStateOf(current.leaveTime) }
    var maxKm by remember { mutableFloatStateOf(current.maxDistanceKm.toFloat()) }

    val timeValid = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matches(leaveTime)

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BrandBar()
        Eyebrow("설정")
        VSpace(6)
        Text("추천 조건", style = MaterialTheme.typography.titleLarge)
        VSpace(16)

        AppCard(padding = 18) {
            Column {
                FieldLabel("직장 위치", "%.4f, %.4f".format(workLat, workLng))
                VSpace(8)
                PresetRow(workLat, workLng) { p -> workLat = p.lat; workLng = p.lng }

                VSpace(18)
                FieldLabel("집 위치", "%.4f, %.4f".format(homeLat, homeLng))
                VSpace(8)
                PresetRow(homeLat, homeLng) { p -> homeLat = p.lat; homeLng = p.lng }

                VSpace(18)
                FieldLabel("퇴근 시각", "HH:MM")
                VSpace(6)
                OutlinedTextField(
                    value = leaveTime,
                    onValueChange = { leaveTime = it },
                    isError = !timeValid,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand.Mint,
                        unfocusedBorderColor = Brand.Line,
                        errorBorderColor = Brand.Coral,
                        focusedContainerColor = Brand.Surface,
                        unfocusedContainerColor = Brand.Surface,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(5)
                Text(
                    if (timeValid) "이 시각 30분 뒤부터 시작하는 강좌를 찾습니다"
                    else "00:00~23:59 형식으로 입력해 주세요",
                    fontSize = 11.5.sp,
                    fontWeight = if (timeValid) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (timeValid) Brand.Muted2 else Brand.Coral,
                )

                VSpace(18)
                FieldLabel("퇴근 동선에서 최대 거리", "%.1fkm".format(maxKm), highlight = true)
                Slider(
                    value = maxKm,
                    onValueChange = { maxKm = (it * 2).roundToInt() / 2f },
                    valueRange = 0.5f..10f,
                    colors = SliderDefaults.colors(
                        thumbColor = Brand.MintDeep,
                        activeTrackColor = Brand.MintDeep,
                        inactiveTrackColor = Brand.Line,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "직장과 집을 잇는 직선에서 이 거리 안의 강좌만 추천합니다",
                    fontSize = 11.5.sp,
                    color = Brand.Muted2,
                )
            }
        }

        VSpace(12)
        AppCard(padding = 18) {
            Column {
                Text(
                    "기기 정보",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                VSpace(10)
                Text("기기 UUID", fontSize = 11.5.sp, color = Brand.Muted)
                Text(deviceId, fontSize = 11.sp, color = Brand.Muted2)
                VSpace(8)
                Text("서버 주소", fontSize = 11.5.sp, color = Brand.Muted)
                Text(BuildConfig.BASE_URL, fontSize = 11.sp, color = Brand.Muted2)
            }
        }

        VSpace(22)
        PrimaryButton(
            "저장",
            onClick = {
                onSave(
                    Settings(
                        workLat = workLat, workLng = workLng,
                        homeLat = homeLat, homeLng = homeLng,
                        leaveTime = leaveTime, maxDistanceKm = maxKm.toDouble(),
                    )
                )
            },
            enabled = timeValid,
        )
        VSpace(9)
        GhostButton("홈으로", onBack)
        VSpace(40)
    }
}

@Composable
private fun FieldLabel(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Brand.Muted,
        )
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) Brand.MintDeep else Brand.Muted2,
        )
    }
}

/** 지도 화면이 이번 범위 밖이라, 좌표를 프리셋 칩으로 고른다. */
@Composable
private fun PresetRow(lat: Double, lng: Double, onPick: (PlacePreset) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PLACE_PRESETS.forEach { p ->
            val selected = abs(p.lat - lat) < 0.0005 && abs(p.lng - lng) < 0.0005
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Brand.Ink else Brand.TrackBg)
                    .clickable { onPick(p) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) androidx.compose.ui.graphics.Color.White else Brand.Muted,
                )
            }
        }
    }
}
