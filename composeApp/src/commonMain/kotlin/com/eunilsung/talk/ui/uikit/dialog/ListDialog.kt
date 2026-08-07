package com.eunilsung.talk.ui.uikit.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_right_icon
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun ListDialog(
    titleTxt: String,
    list: List<String>,
    destructiveItems: Set<String> = emptySet(),
    submenuItems: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            color = AppColors.BgSub
        ) {
            Column(
                modifier = Modifier.padding(15.dp).fillMaxWidth()
            ) {
                Text(
                    text = titleTxt,
                    color = AppColors.Text,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                list.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable { onOptionSelected(option) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.weight(1f),
                            color = if (option in destructiveItems) AppColors.Out
                                    else AppColors.Text,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (option in submenuItems) {
                            Icon(
                                painter = painterResource(Res.drawable.arrow_right_icon),
                                contentDescription = null,
                                tint = AppColors.TextSub,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ListDialogPreview() {
    MaterialTheme {
        ListDialog(
            titleTxt = "title",
            list = listOf("1", "2", "3"),
            onDismiss = {},
            onOptionSelected = {}
        )
    }
}
