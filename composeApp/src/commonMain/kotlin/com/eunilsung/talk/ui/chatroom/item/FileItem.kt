package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.file_default
import multiplatformtalk.composeapp.generated.resources.file_excel
import multiplatformtalk.composeapp.generated.resources.file_image
import multiplatformtalk.composeapp.generated.resources.file_pdf
import multiplatformtalk.composeapp.generated.resources.file_ppt
import multiplatformtalk.composeapp.generated.resources.file_word
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun FileItem(
    itemProps: ChatItemProps
) {
    val chat = itemProps.chat
    val showProgress = chat.isMe &&
        chat.chatStatue == Chat.Statue.SENDING &&
        chat.uploadProgress in 0..100

    ChatBubbleRow(
        itemProps= itemProps,
        modifier = Modifier
        ) {
        Box {
            Row(
                modifier = itemProps
                    .bubbleWidthCap()
                    .widthIn(max = 240.dp)
                    .background(color = AppColors.White, shape = itemProps.bubbleShape())
                    .combinedClickable(
                        onClick = itemProps.onFileClick,
                        onLongClick = itemProps.onLongClick,
                    )
            ) {
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(10.dp)
                    .align(Alignment.CenterVertically)
                ){
                    Text(
                        text = itemProps.chat.originalFileName,
                        color = AppColors.Black,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (itemProps.chat.chatContent.isNotBlank()) {
                        Spacer(modifier = Modifier.size(5.dp))

                        Text(
                            text = itemProps.chat.chatContent,
                            color = AppColors.TextSub,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(modifier = Modifier.padding(top = 10.dp, bottom = 10.dp, end = 5.dp )){
                    Icon(
                        painter = painterResource(fileIconFor(itemProps.chat.originalFileName)),
                        contentDescription = "File Icon",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }
            if (showProgress) {
                UploadProgressOverlay(
                    progress = chat.uploadProgress,
                    shape = itemProps.bubbleShape(),
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

private fun fileIconFor(fileName: String): DrawableResource {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "xls", "xlsx", "xlsm", "xlsb", "csv" -> Res.drawable.file_excel
        "doc", "docx" -> Res.drawable.file_word
        "ppt", "pptx" -> Res.drawable.file_ppt
        "pdf" -> Res.drawable.file_pdf
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "tif", "tiff" -> Res.drawable.file_image
        else -> Res.drawable.file_default
    }
}

@Preview(showBackground = true)
@Composable
fun FileItemPreview() {
    MaterialTheme {
        FileItem(ChatItemProps(
            chat = Chat.Item(
                originalFileName = "File_Name.pdf",
                chatContent = "100MB"
            )
        ))
    }
}

@Preview(showBackground = true)
@Composable
fun FileItemUploadingPreview() {
    MaterialTheme {
        FileItem(ChatItemProps(
            chat = Chat.Item(
                originalFileName = "File_Name.pdf",
                chatContent = "100MB",
                chatStatue = Chat.Statue.SENDING,
                uploadProgress = 65,
            )
        ))
    }
}
