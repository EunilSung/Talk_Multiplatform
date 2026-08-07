package com.eunilsung.talk.ui.chatroom.input

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_hint
import multiplatformtalk.composeapp.generated.resources.emoticon_icon
import multiplatformtalk.composeapp.generated.resources.multimedia_dark_icon
import multiplatformtalk.composeapp.generated.resources.multimedia_light_icon
import multiplatformtalk.composeapp.generated.resources.send_dark_icon
import multiplatformtalk.composeapp.generated.resources.send_light_icon
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMultimediaClick: () -> Unit = {},
    onEmoticonClick: () -> Unit = {},
    enterToSend: Boolean = false,
    showMultimedia: Boolean = true,
    showEmoticon: Boolean = true,
    hasEmoticon: Boolean = false,
    focusRequester: FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fontSize: TextUnit = 13.sp,
) {
    val text = value.text
    val canSend = text.isNotBlank() || hasEmoticon
    val trySend: () -> Unit = { if (canSend) onSendClick() }
    val isDark = isSystemInDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMultimedia) {
            IconButton(
                modifier = Modifier.size(36.dp),
                onClick = onMultimediaClick
            ) {
                Icon(
                    painter = painterResource(if (isDark) Res.drawable.multimedia_dark_icon else Res.drawable.multimedia_light_icon),
                    contentDescription = "Multimedia",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        Row(
            modifier = Modifier
                .background(
                    color = AppColors.Gray50Bg,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(vertical = 2.dp)
                .heightIn(min = 35.dp)
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ){
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 15.dp, end = 5.dp)
                    .heightIn(min = 20.dp, max = 80.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { new ->
                        if (new.text.length <= Chat.MAX_TEXT_LENGTH) onValueChange(new)
                    },
                    textStyle = TextStyle(
                        fontSize = fontSize,
                        color = AppColors.Text
                    ),
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(AppColors.Main),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { trySend() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (enterToSend &&
                                event.key == Key.Enter &&
                                event.type == KeyEventType.KeyDown &&
                                !event.isShiftPressed
                            ) {
                                trySend()
                                true
                            } else false
                        },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.chat_hint),
                                    color = AppColors.TextHint,
                                    fontSize = fontSize,
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            if (showEmoticon) {
                IconButton(
                    modifier = Modifier.size(28.dp),
                    onClick = onEmoticonClick) {
                    Icon(
                        painter = painterResource(Res.drawable.emoticon_icon),
                        contentDescription = "Emoticon",
                        tint = AppColors.TextSub,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(
            modifier = Modifier.size(28.dp),
            onClick = { trySend() },
            enabled = canSend
        ) {
            Icon(
                painter = painterResource(if (isDark) Res.drawable.send_dark_icon else Res.drawable.send_light_icon),
                contentDescription = "Send",
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatInputBarPreview() {
    ChatInputBar(
        value = TextFieldValue(""),
        onValueChange = {},
        onSendClick = {}
    )
}
