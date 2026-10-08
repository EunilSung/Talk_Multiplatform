package com.eunilsung.talk.ui.userprofile.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.itemClickable

/**
 * 프로필 상세의 정보 한 줄 — 항목 이름과 값.
 *
 * @param isCopyable true 면 줄을 눌러 값을 클립보드에 복사한다. 값이 비어 있으면("-" 표시) 눌리지 않는다.
 */
@Composable
fun InfoRow(label: String, value: String, isCopyable: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val canCopy = isCopyable && value.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 15.dp, top = 5.dp)
            .then(
                if (canCopy) Modifier.itemClickable(onClick = { clipboard.setText(AnnotatedString(value)) })
                else Modifier
            )
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .heightIn(min = 40.dp - 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(min = 80.dp),
            fontSize = 13.sp,
            color = AppColors.Text
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value.ifBlank { "-" },
            fontSize = 13.sp,
            color = AppColors.TextSub,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoRowPreview() {
    MaterialTheme {
        InfoRow(label = "이메일", value = "user@example.com")
    }
}
