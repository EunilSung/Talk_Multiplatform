package com.eunilsung.talk.ui.setting.component

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_right_icon
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import com.eunilsung.talk.ui.uikit.checkbox.ToggleV2
import com.eunilsung.talk.ui.uikit.click.itemClickable
import org.jetbrains.compose.resources.painterResource


private val ROW_HPAD = 26.dp
private val ROW_VPAD = 12.dp
private val ROW_MIN_HEIGHT = 56.dp
private val TITLE_SIZE = 15.sp
private val SUB_SIZE = 13.sp


@Composable
fun SettingItemText(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingRow(title = title, modifier = modifier, onClick = onClick.takeIf { enabled })
}

@Composable
fun SettingItemRadio(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        modifier = modifier,
        onClick = onClick.takeIf { enabled },
        titleMaxLines = 1,
    ) {
        Spacer(Modifier.width(12.dp))
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = AppColors.Main),
        )
    }
}

@Composable
fun SettingItemToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) }.takeIf { enabled },
    ) {
        Spacer(Modifier.width(12.dp))
        ToggleV2(checked = checked, onClick = { if (enabled) onCheckedChange(!checked) })
    }
}


@Composable
fun SettingItemButton(
    title: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    // 행 클릭은 걸지 않는다 — 버튼만 눌려야 한다.
    SettingRow(title = title, modifier = modifier) {
        Spacer(Modifier.width(12.dp))
        ButtonV1(
            text = buttonText,
            containerColor = if (destructive) AppColors.Out else AppColors.Main,
            contentColor = AppColors.White,
            onClick = { if (enabled) onClick() }
        )
    }
}


@Composable
fun SettingItemToggleWithDescription(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) }.takeIf { enabled },
        titleColor = AppColors.Text,
        description = description,
    ) {
        Spacer(Modifier.width(12.dp))
        ToggleV2(checked = checked, onClick = { if (enabled) onCheckedChange(!checked) })
    }
}


@Composable
fun SettingItemNavigate(
    title: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.itemClickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
            .padding(horizontal = ROW_HPAD, vertical = ROW_VPAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = TITLE_SIZE,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        if (valueText.isNotBlank()) {
            Text(
                text = valueText,
                fontSize = SUB_SIZE,
                color = AppColors.TextSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            painter = painterResource(Res.drawable.arrow_right_icon),
            contentDescription = null,
            tint = AppColors.TextSub,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingItemsPreview() {
    MaterialTheme {
        Column {
            SettingItemText(title = "로그인/보안", onClick = {})
            SettingItemToggle(title = "소리 알림", checked = true, onCheckedChange = {})
            SettingItemToggleWithDescription(
                title = "미리보기",
                description = "도착알림 시 메시지 내용을 보여줍니다",
                checked = false,
                onCheckedChange = {}
            )
            SettingItemButton(title = "모바일 기기에서 로그아웃 합니다", buttonText = "로그아웃", onClick = {})
            SettingItemNavigate(title = "알림음", valueText = "띵동", onClick = {})
        }
    }
}

/**
 * 설정 행 공용 스캐폴드 — 좌측 제목(+설명), 우측 [trailing].
 *
 * [onClick] 이 null 이면 행 전체 클릭을 걸지 않는다. 버튼형 행은 버튼만 눌려야 하므로
 * `enabled` 하나로 묶으면 행 전체가 눌리는 회귀가 난다.
 */
@Composable
private fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    titleMaxLines: Int = 2,
    description: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.itemClickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
            .padding(horizontal = ROW_HPAD, vertical = ROW_VPAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                fontSize = TITLE_SIZE,
                color = if (titleColor != Color.Unspecified) titleColor
                        else MaterialTheme.colorScheme.onBackground,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = description, fontSize = SUB_SIZE, color = AppColors.TextSub)
            }
        }
        trailing()
    }
}
