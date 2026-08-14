package com.fitbalance.app.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitbalance.app.data.DiagnoseRequest
import com.fitbalance.app.ui.components.HSpace
import com.fitbalance.app.ui.components.VSpace

/** 입력 항목 정의: 라벨, 단위, 허용 범위, 도움말. docs/서비스_아이디어.md의 측정 항목 표를 따른다. */
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val allValid = FIELDS.all { values[it.key].orEmpty().isNotBlank() && errorOf(it) == null }
    fun num(key: String): Double = values[key]?.toDoubleOrNull() ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("체력 측정값 입력") },
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("성별", fontWeight = FontWeight.Bold)
                TextButton(onClick = { FIELDS.forEach { values[it.key] = it.sample } }) {
                    Text("예시값 채우기")
                }
            }
            Row {
                FilterChip(
                    selected = gender == "M",
                    onClick = { gender = "M" },
                    label = { Text("남성") },
                )
                HSpace(8)
                FilterChip(
                    selected = gender == "F",
                    onClick = { gender = "F" },
                    label = { Text("여성") },
                )
            }
            VSpace(16)

            FIELDS.forEach { f ->
                val err = errorOf(f)
                OutlinedTextField(
                    value = values[f.key].orEmpty(),
                    onValueChange = { values[f.key] = it },
                    label = { Text("${f.label} (${f.unit})") },
                    supportingText = {
                        Text(
                            err
                                ?: f.hint.ifBlank { "${f.min.fmt()} ~ ${f.max.fmt()}${f.unit}" }
                        )
                    },
                    isError = err != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (f.key == "sitreach") KeyboardType.Text else KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(4)
            }

            VSpace(12)
            Button(
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("진단하기")
            }
            if (!allValid) {
                VSpace(6)
                Text(
                    "모든 항목을 허용 범위 안의 숫자로 입력해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VSpace(32)
        }
    }
}
