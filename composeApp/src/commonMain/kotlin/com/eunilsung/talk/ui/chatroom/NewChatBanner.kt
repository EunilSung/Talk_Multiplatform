package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.emoticon.StaticEmoticonImage
import com.eunilsung.talk.ui.uikit.emoticon.emoticonResourceNameForId
import com.eunilsung.talk.ui.uikit.user.UserItemV2
import com.eunilsung.talk.util.chatDisplayText
import com.eunilsung.talk.util.splitLeadingToken

/**
 * 목록을 위로 올려 보는 동안 들어온 상대 대화 — 누르면 최신 대화로 내려간다.
 *
 * 대화 앞의 이모티콘 id 토큰은 떼어 내 이미지로 그린다.
 */
@Composable
internal fun NewChatBanner(
    chat: Chat.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview by produceState("", chat) {
        value = chatDisplayText(chat)
    }
    val (emoticon, text) = remember(preview) {
        val trimmed = preview.trim()
        val (token, rest) = splitLeadingToken(trimmed)
        val resource = token?.let { emoticonResourceNameForId(it) }
        if (resource != null) resource to rest else null to trimmed
    }
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.BgSub)
            .border(1.dp, AppColors.Line, RoundedCornerShape(12.dp)),
    ) {
        UserItemV2(
            userId = chat.user.id,
            name = chat.user.name,
            content = text,
            leadingContent = emoticon?.let { resource ->
                {
                    StaticEmoticonImage(
                        resourceName = resource,
                        contentDescription = null,
                        modifier = Modifier.height(18.dp),
                    )
                }
            },
            onClick = onClick,
        )
    }
}
