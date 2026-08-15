package com.fitbalance.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.data.ApiClient
import com.fitbalance.app.data.Place
import com.fitbalance.app.ui.theme.Brand
import kotlinx.coroutines.delay

/** 이만큼 입력이 멈추면 검색한다. 글자마다 부르면 서버가 시끄럽다. */
private const val DEBOUNCE_MS = 250L

/**
 * 지역을 검색해 고르는 입력창.
 *
 * 전국 시군구·동을 가로 스크롤 칩으로는 담을 수 없어 검색으로 바꿨다.
 * 고르고 나면 결과 목록을 접고 고른 곳만 카드로 남긴다.
 *
 * @param label "직장 위치" 같은 제목
 * @param selected 이미 고른 곳. 없으면 null
 */
@Composable
fun PlacePicker(
    label: String,
    hint: String,
    selected: Place?,
    onPick: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }

    // 빈 검색창만 두면 무엇을 쳐야 할지 몰라 막힌다. 시도별 대표 지역을 먼저 띄운다.
    var popular by remember { mutableStateOf<List<Place>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { ApiClient.service.places(null) }.onSuccess { popular = it.items }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            failed = null
            return@LaunchedEffect
        }
        delay(DEBOUNCE_MS)
        searching = true
        failed = null
        runCatching { ApiClient.service.places(q) }
            .onSuccess { results = it.items }
            .onFailure {
                results = emptyList()
                failed = "검색하지 못했습니다. 서버 연결을 확인해 주세요."
            }
        searching = false
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Brand.Muted,
        )
        VSpace(8)

        if (selected != null && query.isBlank()) {
            SelectedCard(selected) { query = " " }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(hint, color = Brand.Muted2) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = Brand.Muted2, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close, "지우기",
                            tint = Brand.Muted2,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { query = "" },
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand.Mint,
                    unfocusedBorderColor = Brand.Line,
                    focusedContainerColor = Brand.Surface,
                    unfocusedContainerColor = Brand.Surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            val q = query.trim()
            AnimatedVisibility(visible = q.length >= 2) {
                Column {
                    VSpace(8)
                    when {
                        failed != null -> Hint(failed!!, Brand.Coral)
                        searching && results.isEmpty() -> Hint("찾는 중...", Brand.Muted2)
                        results.isEmpty() -> Hint("결과가 없습니다. 동 이름이나 구 이름으로 찾아보세요.", Brand.Muted2)
                        else -> ResultList(results) {
                            onPick(it)
                            query = ""
                        }
                    }
                }
            }

            if (q.isEmpty() && popular.isNotEmpty()) {
                VSpace(10)
                Hint("시·도별로 먼저 고르기", Brand.Muted)
                VSpace(6)
                ResultList(popular) { onPick(it); query = "" }
                VSpace(8)
                Hint("동 이름으로 바로 찾아도 됩니다. 예) 전포동 · 망원동", Brand.Muted2)
            }
        }
    }
}

@Composable
private fun SelectedCard(place: Place, onChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brand.MintSoft)
            .clickable(onClick = onChange)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(Brand.MintDeep),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            HSpace(10)
            Text(
                place.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Brand.MintDeep,
            )
        }
        Text("변경", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.MintDeep)
    }
}

@Composable
private fun ResultList(results: List<Place>, onPick: (Place) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brand.Surface)
    ) {
        // 결과가 많아도 화면을 다 먹지 않게 높이를 묶고 안에서만 스크롤한다.
        // 줄 하나가 약 58dp라 네 줄 반쯤 보인다. 더 있다는 게 눈에 보이는 편이 낫다.
        Column(Modifier.heightIn(max = 262.dp).verticalScroll(rememberScrollState())) {
            results.forEachIndexed { i, p ->
                if (i > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .size(1.dp)
                            .background(Brand.Line)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(p) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.dong ?: p.sigungu,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        VSpace(2)
                        Text(p.label, fontSize = 11.5.sp, color = Brand.Muted2)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
