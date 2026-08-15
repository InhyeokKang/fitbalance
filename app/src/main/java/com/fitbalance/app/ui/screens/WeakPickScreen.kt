package com.fitbalance.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 체력요인과 그 요인을 재는 공단 측정항목.
 *
 * 측정항목 이름을 같이 보여주는 이유: 체력인증센터 결과지에는 체력요인이 아니라
 * 측정항목 이름으로 등급이 찍혀 나온다. 결과지를 보면서 그대로 짚을 수 있어야 한다.
 */
private data class WeakOption(
    val key: String,
    val label: String,
    val item: String,
)

private val OPTIONS = listOf(
    WeakOption("strength", "근력", "상대악력"),
    WeakOption("endurance", "근지구력", "교차윗몸일으키기"),
    WeakOption("flex", "유연성", "앉아윗몸앞으로굽히기"),
    WeakOption("cardio", "심폐지구력", "왕복오래달리기"),
    WeakOption("power", "순발력", "제자리멀리뛰기"),
)

/** 이보다 많이 고르면 추천이 특정 약점에 집중되지 않는다. */
private const val ADVISED_MAX = 2

/**
 * 측정값 입력 없이 약점만 골라 바로 강좌를 받는 화면.
 *
 * 체력인증센터에서 측정하면 결과지에 항목별 등급이 이미 나온다.
 * 그 값을 앱에 다시 옮겨 적게 하는 것은 중복이므로, 결과지를 든 사용자는
 * 약한 항목만 짚고 바로 추천으로 넘어가게 한다.
 */
@Composable
fun WeakPickScreen(
    onBack: () -> Unit,
    onSelfCheck: () -> Unit,
    onSubmit: (List<String>) -> Unit,
) {
    val picked = remember { mutableStateListOf<String>() }
    val tooMany = picked.size > ADVISED_MAX

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BrandBar()

        Eyebrow("측정 결과지가 있다면")
        VSpace(6)
        Text("약한 항목만 고르면 됩니다", style = MaterialTheme.typography.titleLarge)
        VSpace(16)

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brand.MintSoft)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(MascotMood.SEARCHING, size = 58, bodyColor = Brand.Mint)
                HSpace(12)
                Text(
                    "체력인증센터에서 받은 결과지에 등급이 낮게 나온 항목을 고르세요. " +
                        "측정값을 다시 옮겨 적을 필요 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.MintDeep,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        VSpace(18)
        AppCard(padding = 14) {
            Column {
                OPTIONS.forEachIndexed { i, o ->
                    if (i > 0) VSpace(8)
                    WeakRow(
                        option = o,
                        selected = o.key in picked,
                        onToggle = {
                            if (o.key in picked) picked.remove(o.key) else picked.add(o.key)
                        },
                    )
                }
            }
        }

        VSpace(10)
        Text(
            if (tooMany) "${ADVISED_MAX}개까지가 좋습니다. 많이 고를수록 추천이 흐려집니다."
            else "가장 약한 1~${ADVISED_MAX}개를 고르는 것이 좋습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = if (tooMany) Brand.Coral else Brand.Muted2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        VSpace(20)
        PrimaryButton(
            if (picked.isEmpty()) "약한 항목을 골라 주세요" else "이 약점에 맞는 강좌 보기",
            { onSubmit(picked.toList()) },
            enabled = picked.isNotEmpty(),
        )
        VSpace(9)
        GhostButton("잘 모르겠어요 · 간편 자가진단 1분", onSelfCheck)
        VSpace(9)
        GhostButton("뒤로", onBack)
        VSpace(40)
    }
}

@Composable
private fun WeakRow(
    option: WeakOption,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Brand.MintSoft else Brand.Bg)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Brand.Mint else Brand.Line,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (selected) Brand.MintDeep else Brand.Line)
            )
            HSpace(12)
            Text(
                option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) Brand.MintDeep else Brand.Ink,
            )
        }
        Text(
            option.item,
            fontSize = 11.sp,
            color = if (selected) Brand.MintDeep.copy(alpha = 0.7f) else Brand.Muted2,
        )
    }
}
