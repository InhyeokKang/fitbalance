package com.fitbalance.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.DiagnoseRequest
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

/** 입력 항목 정의. docs/서비스_아이디어.md의 측정 항목 표를 따른다. */
private data class MeasureField(
    val key: String,
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val hint: String,
    val sample: String,
)

private val FIELDS = listOf(
    MeasureField("age", "나이", "세", 19.0, 64.0, "만 19~64세", "34"),
    MeasureField("height", "키", "cm", 100.0, 250.0, "", "175"),
    MeasureField("weight", "몸무게", "kg", 30.0, 200.0, "", "78"),
    MeasureField("grip", "악력", "kg", 5.0, 120.0, "양손 중 높은 값", "45"),
    MeasureField("situp", "교차윗몸일으키기", "회", 0.0, 100.0, "60초간 횟수", "38"),
    MeasureField("sitreach", "앉아윗몸앞으로굽히기", "cm", -30.0, 40.0, "음수 입력 가능", "6.5"),
    MeasureField("shuttle", "왕복오래달리기", "회", 0.0, 120.0, "20m 셔틀런 횟수", "52"),
    MeasureField("balance", "눈감고외발서기", "초", 0.0, 120.0, "최대 120초", "21"),
)

private fun Double.fmt(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

@Composable
fun MeasureScreen(
    deviceId: String,
    onBack: () -> Unit,
    onSubmit: (DiagnoseRequest) -> Unit,
) {
    var gender by remember { mutableStateOf("M") }
    val values = remember {
        mutableStateMapOf<String, String>().apply { FIELDS.forEach { put(it.key, "") } }
    }

    fun errorOf(f: MeasureField): String? {
        val raw = values[f.key].orEmpty()
        if (raw.isBlank()) return null // 입력 전에는 오류로 표시하지 않는다
        val v = raw.toDoubleOrNull() ?: return "숫자만 입력해 주세요"
        if (v < f.min || v > f.max) return "${f.min.fmt()}~${f.max.fmt()}${f.unit} 범위로 입력해 주세요"
        return null
    }

    val done = FIELDS.count { values[it.key].orEmpty().isNotBlank() && errorOf(it) == null }
    val allValid = done == FIELDS.size
    fun num(key: String): Double = values[key]?.toDoubleOrNull() ?: 0.0

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BrandBar()

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Eyebrow("STEP 1")
                VSpace(6)
                Text("체력 측정값 입력", style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = { FIELDS.forEach { values[it.key] = it.sample } }) {
                Text(
                    "예시값 채우기",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.MintDeep,
                )
            }
        }

        ProgressRow(done, FIELDS.size)
        VSpace(8)

        AppCard(padding = 18) {
            Column {
                Text(
                    "성별",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand.Muted,
                )
                VSpace(8)
                SegmentedGender(gender) { gender = it }

                FIELDS.forEach { f ->
                    val err = errorOf(f)
                    VSpace(14)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            f.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Brand.Muted,
                        )
                        Text(f.unit, fontSize = 11.sp, color = Brand.Muted2)
                    }
                    VSpace(6)
                    OutlinedTextField(
                        value = values[f.key].orEmpty(),
                        onValueChange = { values[f.key] = it },
                        placeholder = {
                            Text(
                                "${f.min.fmt()} ~ ${f.max.fmt()}",
                                color = Brand.Muted2,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        isError = err != null,
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (f.key == "sitreach") KeyboardType.Text else KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VSpace(5)
                    Text(
                        err ?: f.hint.ifBlank { "${f.min.fmt()} ~ ${f.max.fmt()}${f.unit}" },
                        fontSize = 11.5.sp,
                        fontWeight = if (err != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (err != null) Brand.Coral else Brand.Muted2,
                    )
                }
            }
        }

        VSpace(22)
        PrimaryButton(
            text = if (allValid) "진단하기" else "${FIELDS.size - done}개 항목이 남았습니다",
            onClick = {
                onSubmit(
                    DiagnoseRequest(
                        deviceId = deviceId,
                        gender = gender,
                        age = num("age").toInt(),
                        heightCm = num("height"),
                        weightKg = num("weight"),
                        gripKg = num("grip"),
                        sitUp = num("situp").toInt(),
                        sitReachCm = num("sitreach"),
                        shuttleRun = num("shuttle").toInt(),
                        oneLegStandSec = num("balance"),
                    )
                )
            },
            enabled = allValid,
        )
        VSpace(9)
        GhostButton("뒤로", onBack)
        VSpace(40)
    }
}

@Composable
private fun ProgressRow(done: Int, total: Int) {
    val ratio by animateFloatAsState(
        targetValue = done.toFloat() / total,
        animationSpec = tween(400),
        label = "progress",
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$done/$total",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Brand.Muted,
        )
        Box(
            Modifier
                .padding(start = 10.dp)
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brand.Line)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brand.PrimaryGradient)
            )
        }
    }
}

/** 세그먼트 컨트롤. 두 값 선택에는 FilterChip보다 눈이 덜 피로하다. */
@Composable
private fun SegmentedGender(selected: String, onSelect: (String) -> Unit) {
    val trackColor = Color(0xFFEBEFEE)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .padding(4.dp)
    ) {
        listOf("M" to "남성", "F" to "여성").forEach { (code, label) ->
            val on = selected == code
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Brand.Surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(code) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) Brand.Ink else Brand.Muted,
                )
            }
        }
    }
}
