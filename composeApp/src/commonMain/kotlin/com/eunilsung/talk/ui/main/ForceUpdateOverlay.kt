package com.eunilsung.talk.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.update_required
import multiplatformtalk.composeapp.generated.resources.update_required_desc
import multiplatformtalk.composeapp.generated.resources.update
import multiplatformtalk.composeapp.generated.resources.cancel
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eunilsung.talk.ui.theme.AppColors

@Composable
fun ForceUpdateOverlay(
    updateUrl: String,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        ForceUpdateContent(updateUrl = updateUrl, onUpdate = onUpdate, onCancel = onCancel)
    }
}

@Composable
private fun ForceUpdateContent(
    updateUrl: String,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(255.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.BgSub
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.update_required),
                fontSize = 14.sp,
                color = AppColors.Text,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.update_required_desc),
                fontSize = 13.sp,
                color = AppColors.TextSub,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(Res.string.cancel), color = AppColors.Text)
                }
                Button(
                    onClick = onUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Main,
                        contentColor = androidx.compose.ui.graphics.Color.Black,
                    ),
                    enabled = updateUrl.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(text = stringResource(Res.string.update), color = AppColors.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForceUpdateContentPreview() {
    MaterialTheme {
        ForceUpdateContent(updateUrl = "https://store.example.com/app", onUpdate = {}, onCancel = {})
    }
}
