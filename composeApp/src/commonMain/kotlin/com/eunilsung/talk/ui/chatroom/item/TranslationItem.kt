package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.ui.chatroom.ChatTranslationUiState
import com.eunilsung.talk.ui.theme.AppColors
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_translating
import org.jetbrains.compose.resources.stringResource

/** 말풍선 바로 아래에 붙는 번역. 받는 동안에는 진행 문구를 보여 준다. */
@Composable
fun TranslationItem(
    translation: ChatTranslationUiState,
    maxWidth: Dp,
    fontSize: TextUnit,
) {
    val isLoading = translation is ChatTranslationUiState.Loading
    Text(
        text = when (translation) {
            ChatTranslationUiState.Loading -> stringResource(Res.string.chat_translating)
            is ChatTranslationUiState.Ready -> translation.text
        },
        modifier = Modifier
            .padding(top = 3.dp)
            .widthIn(max = maxWidth)
            .background(AppColors.Gray50Bg, RoundedCornerShape(8.dp))
            .padding(vertical = 5.dp, horizontal = 10.dp),
        fontSize = fontSize,
        fontStyle = if (isLoading) FontStyle.Italic else FontStyle.Normal,
        color = if (isLoading) AppColors.TextSub else AppColors.Text,
    )
}
