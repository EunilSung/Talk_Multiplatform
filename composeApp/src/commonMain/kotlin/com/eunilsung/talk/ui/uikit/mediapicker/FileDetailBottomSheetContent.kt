package com.eunilsung.talk.ui.uikit.mediapicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.file_label
import multiplatformtalk.composeapp.generated.resources.download
import multiplatformtalk.composeapp.generated.resources.open
import org.jetbrains.compose.resources.stringResource
import multiplatformtalk.composeapp.generated.resources.close_icon
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import org.jetbrains.compose.resources.painterResource

@Composable
fun FileDetailBottomSheetContent(
    fileName: String,
    senderLabel: String = "",
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
) {
    val extension = remember(fileName) {
        val idx = fileName.lastIndexOf('.')
        if (idx in 0..fileName.length - 2) fileName.substring(idx + 1).uppercase() else ""
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.size(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AppColors.Main.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = extension.ifBlank { "FILE" }.take(5),
                    fontSize = if (extension.length <= 3) 18.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Main,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = fileName.ifBlank { "(이름 없음)" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            if (senderLabel.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = senderLabel,
                    fontSize = 13.sp,
                    color = AppColors.LightGray,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ButtonV1(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.download),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onBackground,
                round = 12.dp,
                onClick = onDownload,
            )
            ButtonV1(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.open),
                round = 12.dp,
                onClick = onOpen,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FileDetailBottomSheetContentPreview() {
    MaterialTheme {
        FileDetailBottomSheetContent(
            fileName = "회의자료_2026.pdf",
            senderLabel = "성은일 · 오후 2:13",
            onClose = {},
            onDownload = {},
            onOpen = {},
        )
    }
}
