package com.eunilsung.talk.ui.chatroom.vote.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.vote_title_label
import multiplatformtalk.composeapp.generated.resources.vote_title_hint
import multiplatformtalk.composeapp.generated.resources.vote_option_label
import multiplatformtalk.composeapp.generated.resources.vote_option_hint
import multiplatformtalk.composeapp.generated.resources.vote_option_add
import multiplatformtalk.composeapp.generated.resources.vote_settings_label
import multiplatformtalk.composeapp.generated.resources.vote_multi_select
import multiplatformtalk.composeapp.generated.resources.vote_create
import org.jetbrains.compose.resources.stringResource
import multiplatformtalk.composeapp.generated.resources.plus_bold_icon
import com.eunilsung.talk.domain.model.VoteFormItem
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.chatroom.vote.VoteActions
import com.eunilsung.talk.ui.chatroom.vote.VoteUiState
import org.jetbrains.compose.resources.painterResource

@Composable
fun VoteCreateContent(
    state: VoteUiState.Create,
    onAction: (VoteActions) -> Unit,
) {
    val form = state.form

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionLabel(stringResource(Res.string.vote_title_label))
            Spacer(modifier = Modifier.size(8.dp))
            FormField(
                value = form.title,
                hint = stringResource(Res.string.vote_title_hint),
                onValueChange = { onAction(VoteActions.OnTitleChange(it)) },
            )

            Spacer(modifier = Modifier.size(20.dp))
            SectionLabel(stringResource(Res.string.vote_option_label))
            Spacer(modifier = Modifier.size(8.dp))
            form.items.forEach { item ->
                ItemRow(
                    item = item,
                    removable = form.items.size > 2,
                    onContentChange = { onAction(VoteActions.OnItemContentChange(item.id, it)) },
                    onRemove = { onAction(VoteActions.OnRemoveItem(item.id)) },
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            AddItemButton(onClick = { onAction(VoteActions.OnAddItem) })

            Spacer(modifier = Modifier.size(20.dp))
            SectionLabel(stringResource(Res.string.vote_settings_label))
            Spacer(modifier = Modifier.size(4.dp))
            ToggleRow(stringResource(Res.string.vote_multi_select), form.multiSelect) { onAction(VoteActions.OnToggleMultiSelect(it)) }
        }

        SubmitButton(
            enabled = form.canSubmit && !state.isSubmitting,
            onClick = { onAction(VoteActions.OnSubmitCreate) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FormField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.Line, RoundedCornerShape(10.dp))
            .background(AppColors.BgSub)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = AppColors.Text, fontSize = 14.sp, lineHeight = 15.sp),
            cursorBrush = SolidColor(AppColors.Main),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = hint, color = AppColors.TextHint, fontSize = 14.sp, lineHeight = 15.sp)
                }
                inner()
            },
        )
    }
}

@Composable
private fun ItemRow(
    item: VoteFormItem,
    removable: Boolean,
    onContentChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = item.content,
            hint = stringResource(Res.string.vote_option_hint),
            onValueChange = onContentChange,
            modifier = Modifier.weight(1f),
        )
        if (removable) {
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close_icon),
                    contentDescription = "Remove item",
                    tint = AppColors.TextSub,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddItemButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.plus_bold_icon),
            contentDescription = "Add item",
            tint = AppColors.Main,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = stringResource(Res.string.vote_option_add), color = AppColors.Main, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), color = AppColors.Text, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.White,
                checkedTrackColor = AppColors.Main,
                uncheckedThumbColor = AppColors.White,
                uncheckedTrackColor = AppColors.Line,
                uncheckedBorderColor = AppColors.Line,
            ),
        )
    }
}

@Composable
private fun SubmitButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) AppColors.Main else AppColors.Line)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.vote_create),
            color = AppColors.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
