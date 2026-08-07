package com.eunilsung.talk.ui.uikit.chip

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class ChipItem(
    val text: String,
    val isSelected: Boolean,
    val onClick: () -> Unit,
    /** LazyRow 안정 키 — 미지정 시 text. 이름이 겹칠 수 있는 그룹 칩은 고유 키를 넘길 것. */
    val key: String = text,
    /** 지정 시 텍스트 대신 이 아이콘만 표시 (예: 그룹 편집 버튼). 나머지 칩 UI 는 동일. */
    val icon: DrawableResource? = null,
    val contentDescription: String? = null,
    /** 지정 시 텍스트 오른쪽에 배지 이미지 표시 (예: 안읽음 new_circle). */
    val badge: DrawableResource? = null,
    /** true 면 정사각 사이즈 + 원형으로 렌더 (아이콘 전용 원형 버튼 용). */
    val circular: Boolean = false,
)

@Composable
fun ChipRow(
    items: List<ChipItem>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    spacing: Dp = 8.dp,
    chipRound: Dp = 20.dp
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(
            items = items,
            key = { it.key },
            contentType = { "Chip" }
        ) { chip ->
            ButtonV1(
                text = chip.text,
                content = {
                    if (chip.icon != null) {
                        Icon(
                            painter = painterResource(chip.icon),
                            contentDescription = chip.contentDescription,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (chip.badge != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = chip.text, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Image(
                                painter = painterResource(chip.badge),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Text(text = chip.text, fontSize = 13.sp)
                    }
                },
                isSelected = chip.isSelected,
                onClick = chip.onClick,
                round = if (chip.circular) 100.dp else chipRound,
                contentPadding = if (chip.circular) PaddingValues(0.dp)
                else PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = if (chip.circular) Modifier.size(40.dp) else Modifier
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChipRowPreview() {
    MaterialTheme {
        ChipRow(
            items = listOf(
                ChipItem(text = "전체", isSelected = true, onClick = {}),
                ChipItem(text = "업무", isSelected = false, onClick = {}),
                ChipItem(text = "개인", isSelected = false, onClick = {}),
            )
        )
    }
}
