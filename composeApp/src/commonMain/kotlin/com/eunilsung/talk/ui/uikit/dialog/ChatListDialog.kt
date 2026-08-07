package com.eunilsung.talk.ui.uikit.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import multiplatformtalk.composeapp.generated.resources.empathy_0
import multiplatformtalk.composeapp.generated.resources.empathy_1
import multiplatformtalk.composeapp.generated.resources.empathy_2
import multiplatformtalk.composeapp.generated.resources.empathy_3
import multiplatformtalk.composeapp.generated.resources.empathy_4
import multiplatformtalk.composeapp.generated.resources.empathy_5
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.jetbrains.compose.resources.painterResource


private val EMPATHY_ICONS = listOf(
    Res.drawable.empathy_0, Res.drawable.empathy_1, Res.drawable.empathy_2,
    Res.drawable.empathy_3, Res.drawable.empathy_4, Res.drawable.empathy_5,
)
@Composable
fun ChatListDialog(
    list: List<String>,
    destructiveItems: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onEmpathyClick: (Int) -> Unit = {},
    showEmpathy: Boolean = true,
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
                if (showEmpathy) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EMPATHY_ICONS.forEachIndexed { index, res ->
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .weight(1f)
                                    .clickable { onEmpathyClick(index) },
                            ) {
                                Image(
                                    painter = painterResource(res),
                                    contentDescription = "empathy",
                                    modifier = Modifier.size(20.dp).align(Alignment.Center),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))
                    LineDivider()
                    Spacer(modifier = Modifier.height(5.dp))
                }

                list.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable {
                                onOptionSelected(option)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.weight(1f),
                            color = if (option in destructiveItems) AppColors.Out
                                    else MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatListDialogPreview() {
    MaterialTheme {
        ChatListDialog(
            list = listOf("1", "2", "3"),
            onDismiss = {},
            onOptionSelected = {}
        )
    }
}
