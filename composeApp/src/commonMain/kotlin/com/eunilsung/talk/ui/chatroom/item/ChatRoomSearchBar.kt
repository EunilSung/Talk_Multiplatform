package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.clear_dark
import multiplatformtalk.composeapp.generated.resources.clear_light
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.search_icon
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** ChatRoom 전용 검색바 — [돋보기] [사용자 칩 + 입력필드 + 지우기(X)] [취소]. */
@Composable
fun ChatRoomSearchBar(
    searchedUser: User?,
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onUserRemove: () -> Unit,
    onClearAll: () -> Unit,
    onCancel: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = isSystemInDarkTheme()
    val hasContent = query.isNotEmpty() || searchedUser != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.search_icon),
            contentDescription = "Search",
            tint = AppColors.TextSub,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 40.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(AppColors.TextFieldBg)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchedUser != null) {
                UserChip(name = searchedUser.name, onRemove = onUserRemove)
                Spacer(Modifier.width(8.dp))
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                textStyle = TextStyle(fontSize = 13.sp, color = AppColors.Text),
                singleLine = true,
                cursorBrush = SolidColor(AppColors.PrimaryMain),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch()
                        keyboardController?.hide()
                    }
                ),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = hint,
                                fontSize = 13.sp,
                                color = AppColors.TextHint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )

            if (hasContent) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onClearAll, modifier = Modifier.size(22.dp)) {
                    Icon(
                        painter = painterResource(if (isDark) Res.drawable.clear_dark else Res.drawable.clear_light),
                        contentDescription = "Clear",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.cancel),
            color = AppColors.TextSub,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onCancel() }
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

/** 선택된 사용자 필터 칩 — 흰 pill + 이름 + 제거 X. */
@Composable
private fun UserChip(name: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(AppColors.BgSub)
            .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = name,
            color = AppColors.Text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
            Icon(
                painter = painterResource(Res.drawable.close_icon),
                contentDescription = "Remove user filter",
                tint = AppColors.TextSub,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomSearchBarPreview() {
    MaterialTheme {
        ChatRoomSearchBar(
            searchedUser = null,
            query = "회사",
            hint = "대화 검색",
            onQueryChange = {},
            onSearch = {},
            onUserRemove = {},
            onClearAll = {},
            onCancel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomSearchBarWithUserPreview() {
    MaterialTheme {
        ChatRoomSearchBar(
            searchedUser = User(name = "홍길동"),
            query = "회사",
            hint = "대화 검색",
            onQueryChange = {},
            onSearch = {},
            onUserRemove = {},
            onClearAll = {},
            onCancel = {},
        )
    }
}
