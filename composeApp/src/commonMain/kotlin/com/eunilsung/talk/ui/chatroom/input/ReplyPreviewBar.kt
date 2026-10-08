package com.eunilsung.talk.ui.chatroom.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.reply_to_name
import org.jetbrains.compose.resources.stringResource
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ReplyChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.util.stripMentionTags
import com.eunilsung.talk.ui.uikit.emoticon.StaticEmoticonImage
import com.eunilsung.talk.ui.uikit.emoticon.emoticonResourceNameForId
import com.eunilsung.talk.ui.uikit.image.UrlImage
import org.jetbrains.compose.resources.painterResource

@Composable
fun ReplyPreviewBar(
    target: Chat.Item,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.BgSub)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        ReplyLeading(target)

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(

                text = replyToNameText(target.user.name),
                color = AppColors.Text,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.size(5.dp))
            Text(
                text = previewContent(target),
                color = AppColors.TextSub,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            modifier = Modifier.size(20.dp),
            onClick = onCancel) {
            Icon(
                painter = painterResource(Res.drawable.close_icon),
                contentDescription = "Cancel reply",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private val ReplyLeadingSize = 36.dp

@Composable
private fun ReplyLeading(target: Chat.Item) {
    // 타입이 아니라 실제로 달고 있는 이모티콘 유무로 먼저 판정한다.
    val emoticonName = target.emoticon.id.takeIf { it.isNotBlank() }
        ?.let { emoticonResourceNameForId(it) }
    if (emoticonName != null) {
        Box(modifier = Modifier.padding(end = 10.dp)) {
            StaticEmoticonImage(
                resourceName = emoticonName,
                contentDescription = "emoticon",
                modifier = Modifier.size(ReplyLeadingSize),
            )
        }
        return
    }

    when (target.chatType) {
        Chat.Type.IMAGE -> {
            val isSending = target.chatStatue == Chat.Statue.SENDING
            val hasLocal = isSending && target.localPath.isNotBlank()
            if (target.imagePath.isNotBlank() || hasLocal) {
                val url = if (hasLocal) target.localPath
                else target.imagePath
                Box(modifier = Modifier.padding(end = 10.dp)){
                    UrlImage(
                        url = url,
                        modifier = Modifier
                            .size(ReplyLeadingSize)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFDFE2E6)),
                    )
                }
            }
        }
    }
}

/** 인용 본문 — 멘션 태그만 벗겨 보여준다. */
private fun previewContent(chat: Chat.Item): String = chat.chatContent.stripMentionTags()

@Preview(showBackground = true)
@Composable
fun ReplyPreviewBarPreview() {
    MaterialTheme {
        ReplyPreviewBar(
            target = Chat.Item(
                user = User(
                    name = "성은일"
                ),
            chatContent = "답장 본문"
        ),
            {}
        )
    }
}
/** 예전 버전이 답장 대상의 이름에 직접 붙여 저장하던 접미사 — 이미 저장된 대화에서 떼어 낸다. */
internal const val REPLY_NAME_LEGACY_SUFFIX = "에게 답장"

/** 답장 대상 이름을 "OOO에게 답장" 문구로 — 이름이 비면 빈 문자열. */
@Composable
internal fun replyToNameText(name: String): String =
    if (name.isBlank()) "" else stringResource(Res.string.reply_to_name, name.removeSuffix(REPLY_NAME_LEGACY_SUFFIX))
