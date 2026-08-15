package com.fitbalance.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.HomeDiagnoseRequest
import com.fitbalance.app.ui.components.AppCard
import com.fitbalance.app.ui.components.Eyebrow
import com.fitbalance.app.ui.components.GhostButton
import com.fitbalance.app.ui.components.HSpace
import com.fitbalance.app.ui.components.Mascot
import com.fitbalance.app.ui.components.MascotMood
import com.fitbalance.app.ui.components.PrimaryButton
import com.fitbalance.app.ui.components.VSpace
import com.fitbalance.app.ui.theme.Brand

/**
 * 집에서 재는 항목 하나.
 *
 * [how]는 공단 측정 방법을 집에서 할 수 있게 옮긴 것이다. 자세가 다르면 백분위가
 * 의미를 잃으므로, 무엇을 어떻게 세는지까지 적는다.
 */
private data class HomeField(
    val key: String,
    val label: String,
    val unit: String,
    val hint: String,
    val how: String,
    val need: String,
    val min: Double,
    val max: Double,
    val decimal: Boolean = false,
    val sample: String,
)

private val BODY_FIELDS = listOf(
    HomeField("age", "나이", "세", "19 ~ 64", "", "", 19.0, 64.0, sample = "32"),
    HomeField("height", "키", "cm", "100 ~ 250", "", "", 100.0, 250.0, decimal = true, sample = "175"),
    HomeField("weight", "몸무게", "kg", "30 ~ 200", "", "", 30.0, 200.0, decimal = true, sample = "78"),
)

private val MEASURE_FIELDS = listOf(
    HomeField(
        key = "situp",
        label = "교차윗몸일으키기",
        unit = "회",
        hint = "0 ~ 100",
        how = "무릎을 90도로 세우고 누워 양팔을 가슴 앞에 교차합니다. " +
            "등이 바닥에 닿았다가 팔꿈치가 허벅지에 닿을 때까지 일어나면 1회입니다. " +
            "60초 동안 센 횟수를 넣으세요.",
        need = "바닥 매트, 초시계",
        min = 0.0, max = 100.0, sample = "38",
    ),
    HomeField(
        key = "sitreach",
        label = "앉아윗몸앞으로굽히기",
        unit = "cm",
        hint = "-30 ~ 40",
        how = "다리를 펴고 앉아 발바닥을 벽이나 상자에 붙입니다. 발끝 위치를 0으로 두고 " +
            "반동 없이 천천히 손끝을 밀어 2초 멈춘 지점을 잽니다. " +
            "발끝을 넘어가면 +, 못 미치면 - 입니다.",
        need = "줄자, 벽",
        min = -30.0, max = 40.0, decimal = true, sample = "6.5",
    ),
    HomeField(
        key = "jump",
        label = "제자리멀리뛰기",
        unit = "cm",
        hint = "30 ~ 350",
        how = "두 발을 모으고 서서 팔을 흔들어 앞으로 뜁니다. " +
            "출발선부터 착지한 뒤꿈치 중 뒤쪽까지의 거리를 잽니다. 2번 중 잘 나온 값을 쓰세요.",
        need = "3m 공간, 줄자",
        min = 30.0, max = 350.0, decimal = true, sample = "205",
    ),
)

private val ALL_FIELDS = BODY_FIELDS + MEASURE_FIELDS

private fun Double.fmt(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

/**
 * 집에서 재는 3항목으로 진단하는 화면.
 *
 * 공단 성인기 측정항목 5개 중 악력계와 20m 구간이 필요한 둘은 뺐다.
 * 못 잰 두 요인은 결과 화면에서 센터 안내로 이어진다.
 */
@Composable
fun HomeMeasureScreen(
    deviceId: String,
    onBack: () -> Unit,
    onFindCenter: () -> Unit,
    onSubmit: (HomeDiagnoseRequest) -> Unit,
) {
    var gender by remember { mutableStateOf("M") }
    val values = remember {
        mutableStateMapOf<String, String>().apply { ALL_FIELDS.forEach { put(it.key, "") } }
    }

    fun errorOf(f: HomeField): String? {
        val raw = values[f.key].orEmpty()
        if (raw.isBlank()) return null
        val v = raw.toDoubleOrNull() ?: return "숫자만 입력해 주세요"
        if (v < f.min || v > f.max) return "${f.min.fmt()}~${f.max.fmt()}${f.unit} 범위로 입력해 주세요"
        return null
    }

    val done = ALL_FIELDS.count { values[it.key].orEmpty().isNotBlank() && errorOf(it) == null }
    val allValid = done == ALL_FIELDS.size
    fun num(key: String): Double = values[key]?.toDoubleOrNull() ?: 0.0

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BrandBar()

        Eyebrow("트랙 1 · 집에서")
        VSpace(6)
        Text("도구 두 개면 됩니다", style = MaterialTheme.typography.titleLarge)
        VSpace(14)

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brand.MintSoft)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(MascotMood.WORKOUT, size = 60, bodyColor = Brand.Mint)
                HSpace(12)
                Text(
                    "줄자와 초시계만 있으면 국민체력100 측정항목 3가지를 집에서 잴 수 있습니다. " +
                        "결과는 같은 성별·나이대 실제 측정자료와 대조합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.MintDeep,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        VSpace(16)
        Text(
            "$done/${ALL_FIELDS.size} 입력",
            style = MaterialTheme.typography.labelSmall,
            color = Brand.Muted2,
        )

        VSpace(10)
        AppCard {
            Column {
                Text("기본 정보", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                VSpace(10)
                SegmentedGender(gender) { gender = it }
                BODY_FIELDS.forEach { f ->
                    VSpace(14)
                    FieldRow(f, values[f.key].orEmpty(), errorOf(f)) { values[f.key] = it }
                }
            }
        }

        MEASURE_FIELDS.forEach { f ->
            VSpace(12)
            AppCard {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            f.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("준비물 ${f.need}", fontSize = 11.sp, color = Brand.Muted2)
                    }
                    VSpace(8)
                    Text(
                        f.how,
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Muted,
                    )
                    VSpace(12)
                    FieldRow(f, values[f.key].orEmpty(), errorOf(f)) { values[f.key] = it }
                }
            }
        }

        VSpace(12)
        Text(
            "근력과 심폐지구력은 악력계·20m 구간이 있어야 해서 집에서는 뺐습니다. " +
                "그 둘은 체력인증센터에서 무료로 잽니다.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.Muted2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            textAlign = TextAlign.Center,
        )

        VSpace(20)
        PrimaryButton(
            text = if (allValid) "진단하기" else "${ALL_FIELDS.size - done}개 항목이 남았습니다",
            onClick = {
                onSubmit(
                    HomeDiagnoseRequest(
                        deviceId = deviceId,
                        gender = gender,
                        age = num("age").toInt(),
                        heightCm = num("height"),
                        weightKg = num("weight"),
                        sitUp = num("situp").toInt(),
                        sitReachCm = num("sitreach"),
                        standingJumpCm = num("jump"),
                    )
                )
            },
            enabled = allValid,
        )
        VSpace(9)
        GhostButton("예시값으로 둘러보기", { ALL_FIELDS.forEach { values[it.key] = it.sample } })
        VSpace(9)
        GhostButton("센터에서 제대로 재고 싶어요", onFindCenter)
        VSpace(9)
        GhostButton("뒤로", onBack)
        VSpace(40)
    }
}

@Composable
private fun FieldRow(
    f: HomeField,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                if (f.how.isEmpty()) f.label else "값 입력",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Muted,
            )
            Text(f.unit, fontSize = 11.sp, color = Brand.Muted2)
        }
        VSpace(6)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(f.hint, color = Brand.Muted2) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (f.decimal) KeyboardType.Decimal else KeyboardType.Number,
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Brand.Mint,
                unfocusedBorderColor = Brand.Line,
                errorBorderColor = Brand.Coral,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            VSpace(4)
            Text(error, style = MaterialTheme.typography.bodySmall, color = Brand.Coral)
        }
    }
}
