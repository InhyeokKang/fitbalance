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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.fitbalance.app.data.Place
import com.fitbalance.app.ui.Settings
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.PlacePicker
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.RoundSliderThumb
import com.fitbalance.app.ui.components.TimeField
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand
import kotlin.math.roundToInt

/** 내 기록 삭제 버튼의 상태. */
enum class DeleteState { Idle, Working, Done }

// Slider의 thumb 교체가 아직 실험적 API다. 기본 손잡이가 세로 막대라 직접 그려 쓴다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: Settings,
    deviceId: String,
    /** 지금 쓰는 서버 주소. 사용자가 바꾸지 않았으면 빌드 기본값이다. */
    initialServerUrl: String,
    onBack: () -> Unit,
    onSave: (Settings) -> Unit,
    onSaveServerUrl: (String) -> Unit,
    deleteState: DeleteState = DeleteState.Idle,
    onDeleteMyData: () -> Unit = {},
) {
    // 저장된 값을 Place 로 되살려 검색창에 "이미 고른 곳"으로 보여 준다.
    var work by remember {
        mutableStateOf(current.workLabel.takeIf { it.isNotBlank() }?.let {
            Place(it, it.split(" ").first(), "", null, current.workLat, current.workLng)
        })
    }
    var home by remember {
        mutableStateOf(current.homeLabel.takeIf { it.isNotBlank() }?.let {
            Place(it, it.split(" ").first(), "", null, current.homeLat, current.homeLng)
        })
    }
    var leaveTime by remember { mutableStateOf(current.leaveTime) }
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
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
                PlacePicker(
                    label = "직장 위치",
                    hint = "회사가 있는 동네를 검색하세요",
                    selected = work,
                    onPick = { work = it },
                )

                VSpace(18)
                PlacePicker(
                    label = "집 위치",
                    hint = "사는 동네를 검색하세요",
                    selected = home,
                    onPick = { home = it },
                )

                VSpace(18)
                TimeField(leaveTime, timeValid) { leaveTime = it }

                VSpace(18)
                FieldLabel("퇴근 동선에서 최대 거리", "%.1fkm".format(maxKm), highlight = true)
                Slider(
                    value = maxKm,
                    onValueChange = { maxKm = (it * 2).roundToInt() / 2f },
                    valueRange = 0.5f..10f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Brand.MintDeep,
                        inactiveTrackColor = Brand.Line,
                    ),
                    thumb = { RoundSliderThumb() },
                    // 트랙 끝의 기본 정지 표시 점은 이 디자인에 불필요해 끈다.
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Brand.MintDeep,
                                inactiveTrackColor = Brand.Line,
                            ),
                            drawStopIndicator = null,
                            modifier = Modifier.height(6.dp),
                        )
                    },
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

                // 개인정보 삭제 요구권. 로그인이 없으므로 기기 UUID 가 유일한 식별자다.
                VSpace(14)
                Text(
                    "서버에는 성별·나이대·약한 체력요인만 남습니다. " +
                        "키·몸무게·측정값은 계산에만 쓰이고 저장되지 않습니다.",
                    fontSize = 11.5.sp,
                    color = Brand.Muted2,
                )
                VSpace(10)
                if (deleteState == DeleteState.Done) {
                    Text("지웠습니다.", fontSize = 12.sp, color = Brand.MintDeep)
                } else {
                    GhostButton(
                        if (deleteState == DeleteState.Working) "지우는 중..." else "내 기록 삭제",
                        onDeleteMyData,
                    )
                }
            }
        }

        VSpace(12)
        AppCard(padding = 18) {
            Column {
                Text("서버 주소", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                VSpace(4)
                Text(
                    "진단·추천·검색은 서버가 처리합니다. 꺼져 있으면 동작하지 않습니다.",
                    fontSize = 11.5.sp,
                    color = Brand.Muted2,
                )
                VSpace(10)
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = { Text(BuildConfig.BASE_URL, color = Brand.Muted2) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand.Mint,
                        unfocusedBorderColor = Brand.Line,
                        focusedContainerColor = Brand.Surface,
                        unfocusedContainerColor = Brand.Surface,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(5)
                Text(
                    "에뮬레이터는 그대로 두세요. 폰에 설치했다면 같은 와이파이의 " +
                        "노트북 주소(예: http://192.168.0.15:8000/)를 넣습니다.",
                    fontSize = 11.5.sp,
                    color = Brand.Muted2,
                )
            }
        }

        VSpace(22)
        PrimaryButton(
            "저장",
            onClick = {
                val w = work ?: return@PrimaryButton
                val h = home ?: return@PrimaryButton
                onSaveServerUrl(serverUrl)
                onSave(
                    current.copy(
                        workLat = w.lat, workLng = w.lng, workLabel = w.label,
                        homeLat = h.lat, homeLng = h.lng, homeLabel = h.label,
                        leaveTime = leaveTime, maxDistanceKm = maxKm.toDouble(),
                    )
                )
            },
            enabled = timeValid && work != null && home != null,
        )
        VSpace(9)
        GhostButton("홈으로", onBack)
        VSpace(40)
    }
}

@Composable
private fun FieldLabel(label: String, value: String? = null, highlight: Boolean = false) {
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
        if (value != null) {
            Text(
                value,
                fontSize = 11.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                color = if (highlight) Brand.MintDeep else Brand.Muted2,
            )
        }
    }
}
