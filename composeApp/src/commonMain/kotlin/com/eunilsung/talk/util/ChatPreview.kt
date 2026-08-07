package com.eunilsung.talk.util

import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_preview_file
import multiplatformtalk.composeapp.generated.resources.chat_preview_notice
import multiplatformtalk.composeapp.generated.resources.chat_preview_notice_deleted
import multiplatformtalk.composeapp.generated.resources.chat_preview_photo
import multiplatformtalk.composeapp.generated.resources.chat_preview_vote
import multiplatformtalk.composeapp.generated.resources.chat_preview_vote_closed
import multiplatformtalk.composeapp.generated.resources.chat_recalled
import multiplatformtalk.composeapp.generated.resources.file
import multiplatformtalk.composeapp.generated.resources.photo
import org.jetbrains.compose.resources.getString
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.Notice

// 미리보기 앞쪽 괄호 토큰(이모티콘 id) 매칭용 정규식.
private val LEADING_TOKEN_REGEX = Regex("""^\(([^()]+)\)\s*""")

/** 이모티콘 id + 본문 → 미리보기 문자열. 본문이 없으면 id 만. */
fun buildEmoticonPreview(emoticonId: String, text: String): String =
    if (text.isBlank()) emoticonId else "$emoticonId $text"

/** 미리보기 문자열에서 앞쪽 괄호 토큰을 분리한다. 토큰이 없으면 (null, 원본). */
fun splitLeadingToken(preview: String): Pair<String?, String> {
    val match = LEADING_TOKEN_REGEX.find(preview) ?: return null to preview
    return match.value.trim() to preview.removeRange(match.range).trim()
}

/**
 * 대화 한 줄을 타입별로 요약한 표시 문구.
 * @param forReply 답장 인용문용(사진을 "사진" 으로 축약).
 */
suspend fun chatDisplayText(chat: Chat.Item, forReply: Boolean = false): String = when {
    chat.isRecalled -> getString(Res.string.chat_recalled)

    chat.chatType == Chat.Type.IMAGE || chat.chatType == Chat.Type.MULTI_IMAGE ->
        if (forReply) getString(Res.string.photo) else getString(Res.string.chat_preview_photo)

    chat.chatType == Chat.Type.FILE || chat.chatType == Chat.Type.VIDEO ->
        getString(
            Res.string.chat_preview_file,
            chat.originalFileName.ifBlank { getString(Res.string.file) },
        )

    chat.chatType == Chat.Type.VOTE ->
        getString(Res.string.chat_preview_vote, chat.title)

    chat.chatType == Chat.Type.VOTE_COMPLETE ->
        getString(Res.string.chat_preview_vote_closed, chat.title)

    // 등록/삭제는 title, 본문은 chatContent 에 나뉘어 있다.
    chat.chatType == Chat.Type.NOTICE ->
        getString(
            if (chat.title == Notice.ACTION_DELETE) Res.string.chat_preview_notice_deleted
            else Res.string.chat_preview_notice,
            chat.chatContent.stripMentionTags(),
        )

    // 타입이 아니라 이모티콘을 달고 있는지로 판정(답장+이모티콘도 표기).
    chat.chatType == Chat.Type.EMOTICON || chat.emoticon.id.isNotBlank() ->
        if (forReply) chat.chatContent.stripMentionTags().ifBlank { EMOTICON_LABEL }
        else if (chat.emoticon.id.isBlank()) EMOTICON_LABEL
        else buildEmoticonPreview(chat.emoticon.id, chat.chatContent.stripMentionTags())

    else -> chat.chatContent.stripMentionTags()
}

/** 본문 없는 이모티콘 대화의 대체 표기. */
const val EMOTICON_LABEL = "이모티콘"
