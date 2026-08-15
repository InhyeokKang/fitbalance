package com.fitbalance.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitbalance.app.ui.theme.Brand

/**
 * 퇴근 시각 입력. 첫 실행 화면과 설정 화면이 같은 모양을 쓴다.
 *
 * @param valid 형식이 맞는지. 호출하는 쪽이 판정해 넘긴다(저장 가능 여부도 그 값으로 정하므로).
 */
@Composable
fun TimeField(
    value: String,
    valid: Boolean,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "퇴근 시각",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Brand.Muted,
            )
            Text("HH:MM", fontSize = 11.sp, color = Brand.Muted2)
        }
        VSpace(6)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            isError = !valid,
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
            if (valid) "이 시각 30분 뒤부터 시작하는 강좌를 찾습니다"
            else "00:00~23:59 형식으로 입력해 주세요",
            fontSize = 11.5.sp,
            fontWeight = if (valid) FontWeight.Normal else FontWeight.SemiBold,
            color = if (valid) Brand.Muted2 else Brand.Coral,
        )
    }
}
