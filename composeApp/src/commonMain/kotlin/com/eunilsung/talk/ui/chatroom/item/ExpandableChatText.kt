package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.view_all
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExpandableChatText(
    text: String,
    searchQuery: String,
    modifier: Modifier = Modifier,
    color: Color = AppColors.Black,
    fontSize: TextUnit = 13.sp,
    previewMaxLines: Int = 15,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    var hasOverflow by remember(text, previewMaxLines) { mutableStateOf(false) }
    var showFullView by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val annotated = buildChatHighlightedText(
        text = text,
        searchWord = searchQuery,
        linkColor = AppColors.SkyLine,
        clickableLinks = false,
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Column(modifier = modifier) {
        Text(
            text = annotated,
            color = color,
            fontSize = fontSize,
            maxLines = previewMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                layout = result
                hasOverflow = result.hasVisualOverflow
            },
            modifier = Modifier.pointerInput(annotated) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { pos ->
                        val lr = layout
                        val url = if (lr != null) {
                            val offset = lr.getOffsetForPosition(pos)
                            annotated.getStringAnnotations(URL_ANNOTATION_TAG, offset, offset).firstOrNull()?.item
                        } else null
                        if (url != null) uriHandler.openUri(url) else onClick()
                    }
                )
            },
        )
        if (hasOverflow) {
            Text(
                text = stringResource(Res.string.view_all),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { showFullView = true }
            )
        }
    }

    if (showFullView) {
        FullTextDialog(
            text = text,
            linkColor = AppColors.SkyLine,
            onDismiss = { showFullView = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullTextDialog(
    text: String,
    linkColor: Color,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Bg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBarV1(
                    title = stringResource(Res.string.view_all),
                    navigationIcon = {
                        IconButton(
                            modifier = Modifier.size(28.dp),
                            onClick = onDismiss
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.close_icon),
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )

                LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = buildChatHighlightedText(
                                text = text,
                                searchWord = "",
                                linkColor = linkColor,
                            ),
                            color = AppColors.Text,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExpandableChatTextPreview() {
    MaterialTheme {
        ExpandableChatText(
            text = "안녕하세요. 링크 https://www.example.com 가 포함된 본문 미리보기입니다.\n".repeat(20),
            searchQuery = "링크",
        )
    }
}
