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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.SelfCheckRequest
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
 * 자가진단 문항.
 *
 * 도구 없이 답할 수 있는 것만 넣는다. 의자·줄자 정도까지가 한계다.
 * 선택지는 낮은 것부터 높은 것 순서로 두고, 그 index가 그대로 0~3점이 된다.
 */
private data class SelfQuestion(
    val key: String,
    val factorLabel: String,
    val question: String,
    val hint: String,
    val options: List<String>,
)

private val QUESTIONS = listOf(
    SelfQuestion(
        key = "strength",
        factorLabel = "근력",
        question = "무릎 대고 팔굽혀펴기를 연속 몇 개나 할 수 있나요?",
        hint = "무릎을 바닥에 대고 해도 됩니다",
        options = listOf("4개 이하", "5~9개", "10~19개", "20개 이상"),
    ),
    SelfQuestion(
        key = "endurance",
        factorLabel = "근지구력",
        question = "의자에 앉았다 일어서기를 30초간 몇 번 하나요?",
        hint = "팔짱을 끼고, 등받이에 기대지 않고 세어 보세요",
        options = listOf("9번 이하", "10~14번", "15~19번", "20번 이상"),
    ),
    SelfQuestion(
        key = "flex",
        factorLabel = "유연성",
        question = "선 자세에서 무릎을 펴고 앞으로 굽히면 손끝이 어디까지 닿나요?",
        hint = "반동 주지 말고 천천히 내려가 보세요",
        options = listOf("무릎 위", "무릎~정강이", "발목 근처", "손끝이 바닥에 닿음"),
    ),
    SelfQuestion(
        key = "cardio",
        factorLabel = "심폐지구력",
        question = "계단으로 3층까지 쉬지 않고 오르면 어떤가요?",
        hint = "평소 속도로 올랐을 때를 기준으로",
        options = listOf("중간에 쉬어야 함", "꽤 숨참", "약간 숨참", "거의 숨차지 않음"),
    ),
    SelfQuestion(
        key = "power",
        factorLabel = "순발력",
        question = "계단을 두 칸씩 뛰어오를 수 있나요?",
        hint = "버스를 놓칠 것 같을 때를 떠올려 보세요",
        options = listOf("못 함", "힘듦", "가능함", "쉽게 함"),
    ),
    SelfQuestion(
        key = "activity",
        factorLabel = "활동량",
        question = "일주일에 30분 이상 숨찬 운동을 며칠 하나요?",
        hint = "빠르게 걷기도 포함됩니다",
        options = listOf("거의 안 함", "1~2일", "3~4일", "5일 이상"),
    ),
)

@Composable
fun SelfCheckScreen(
    deviceId: String,
    onBack: () -> Unit,
    onSubmit: (SelfCheckRequest) -> Unit,
) {
    var gender by remember { mutableStateOf("M") }
    var age by remember { mutableStateOf("") }
    val answers = remember { mutableStateMapOf<String, Int>() }

    val ageValue = age.toIntOrNull()
    val ageValid = ageValue != null && ageValue in 19..64
    val answered = answers.size
    val allAnswered = answered == QUESTIONS.size
    val canSubmit = ageValid && allAnswered

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BrandBar()
        Eyebrow("간편 자가진단")
        VSpace(6)
        Text("도구 없이 1분", style = MaterialTheme.typography.titleLarge)
        VSpace(10)

        // 추정치라는 점을 처음부터 분명히 한다. 결과 화면에서도 한 번 더 알린다.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brand.MintSoft)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mascot(MascotMood.SEARCHING, size = 52, bob = false)
            HSpace(8)
            Text(
                "설문으로 약점을 추정합니다. 정확한 진단은 무료 체력인증센터에서 측정한 값을 넣어 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.MintDeep,
                fontWeight = FontWeight.SemiBold,
            )
        }

        ProgressBar(answered, QUESTIONS.size)
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
                Segmented(
                    options = listOf("M" to "남성", "F" to "여성"),
                    selected = gender,
                    onSelect = { gender = it },
                )
                VSpace(14)
                Text(
                    "나이",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand.Muted,
                )
                VSpace(6)
                AgeField(age) { age = it }
                VSpace(5)
                Text(
                    if (age.isBlank() || ageValid) "만 19~64세" else "19~64세 범위로 입력해 주세요",
                    fontSize = 11.5.sp,
                    fontWeight = if (ageValid || age.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (ageValid || age.isBlank()) Brand.Muted2 else Brand.Coral,
                )
            }
        }

        QUESTIONS.forEachIndexed { index, q ->
            VSpace(12)
            QuestionCard(
                number = index + 1,
                question = q,
                selected = answers[q.key],
                onSelect = { answers[q.key] = it },
            )
        }

        VSpace(22)
        PrimaryButton(
            text = when {
                !ageValid -> "나이를 입력해 주세요"
                !allAnswered -> "${QUESTIONS.size - answered}개 문항이 남았습니다"
                else -> "결과 보기"
            },
            onClick = {
                onSubmit(
                    SelfCheckRequest(
                        deviceId = deviceId,
                        gender = gender,
                        age = ageValue ?: 30,
                        strength = answers["strength"] ?: 0,
                        endurance = answers["endurance"] ?: 0,
                        flex = answers["flex"] ?: 0,
                        cardio = answers["cardio"] ?: 0,
                        power = answers["power"] ?: 0,
                        activity = answers["activity"] ?: 0,
                    )
                )
            },
            enabled = canSubmit,
        )
        VSpace(9)
        GhostButton("뒤로", onBack)
        VSpace(40)
    }
}

@Composable
private fun QuestionCard(
    number: Int,
    question: SelfQuestion,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    AppCard(padding = 18) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected != null) Brand.MintDeep else Brand.TrackBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$number",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (selected != null) Color.White else Brand.Muted2,
                    )
                }
                HSpace(8)
                Text(
                    question.factorLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.MintDeep,
                )
            }
            VSpace(8)
            Text(
                question.question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            VSpace(4)
            Text(question.hint, fontSize = 11.5.sp, color = Brand.Muted2)
            VSpace(12)

            question.options.forEachIndexed { score, label ->
                val on = selected == score
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 7.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) Brand.MintSoft else Brand.Bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(score) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (on) Brand.MintDeep else Color.Transparent)
                                .then(
                                    if (on) Modifier
                                    else Modifier.background(Brand.Line, CircleShape)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                        HSpace(10)
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Brand.MintDeep else Brand.Ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(done: Int, total: Int) {
    val ratio by animateFloatAsState(
        targetValue = done.toFloat() / total,
        animationSpec = tween(400),
        label = "selfcheck",
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

@Composable
private fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEBEFEE))
            .padding(4.dp)
    ) {
        options.forEach { (code, label) ->
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

@Composable
private fun AgeField(value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = {
            Text("19 ~ 64", color = Brand.Muted2, style = MaterialTheme.typography.bodyLarge)
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand.Mint,
            unfocusedBorderColor = Brand.Line,
            focusedContainerColor = Brand.Surface,
            unfocusedContainerColor = Brand.Surface,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
