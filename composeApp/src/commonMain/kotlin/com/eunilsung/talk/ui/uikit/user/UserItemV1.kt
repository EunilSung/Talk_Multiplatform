package com.eunilsung.talk.ui.uikit.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.presence_mobile_offline
import multiplatformtalk.composeapp.generated.resources.presence_mobile_online
import multiplatformtalk.composeapp.generated.resources.presence_pc_absence
import multiplatformtalk.composeapp.generated.resources.presence_pc_etc
import multiplatformtalk.composeapp.generated.resources.presence_pc_login
import multiplatformtalk.composeapp.generated.resources.presence_pc_logout
import multiplatformtalk.composeapp.generated.resources.presence_pc_work
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.checkbox.ToggleV1
import com.eunilsung.talk.ui.uikit.click.itemClickable
import com.eunilsung.talk.ui.uikit.image.ProfileImages
import org.jetbrains.compose.resources.painterResource

@Composable
fun UserItemV1(
    user: User,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier.heightIn(min = 70.dp, max = 100.dp),
    isToggleable: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isToggleable && user.isSelect) AppColors.UserSelectBg else AppColors.Transparent)
            .itemClickable(
                cornerRadius = 12.dp,
                onClick = { onClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (isToggleable){
            ToggleV1(
                checked = user.isSelect,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(modifier = Modifier.size(55.dp)) {
            ProfileImages(userIds = listOf(user.id))

            user.presencePc?.let { pcStatus ->
                val pcRes = when (pcStatus) {
                    "1" -> Res.drawable.presence_pc_login
                    "2" -> Res.drawable.presence_pc_absence
                    "3" -> Res.drawable.presence_pc_work
                    "4" -> Res.drawable.presence_pc_etc
                    else -> Res.drawable.presence_pc_logout
                }
                Image(
                    painter = painterResource(pcRes),
                    contentDescription = "presence_pc",
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                user.presenceMobile?.let {
                    Image(
                        painter = painterResource(
                            if (it == "0") Res.drawable.presence_mobile_offline else Res.drawable.presence_mobile_online
                        ),
                        contentDescription = "presence_mobile",
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }

                Text(
                    text = user.name,
                    color = AppColors.Text,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val dept = user.departmentName?.takeIf { it.isNotEmpty() }
                val position = user.positionName?.takeIf { it.isNotEmpty() }
                val deptPositionLabel = when {
                    dept != null && position != null -> "[$dept/$position]"
                    position != null -> "[$position]"
                    dept != null -> "[$dept]"
                    else -> ""
                }
                if (deptPositionLabel.isNotEmpty()) {
                    Text(
                        text = deptPositionLabel,
                        color = AppColors.Text,
                        fontSize = 13.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            if (!user.nickname.isNullOrEmpty()) {
                Text(
                    text = user.nickname,
                    color = AppColors.TextSub,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UserItemV1Preview() {
    MaterialTheme {
        UserItemV1(
            user = User(
                id = "1",
                name = "Eunil Sung",
                departmentName = "Department",
                positionName = "Position",
                nickname = "Nickname",
                isSelect = true,
                presencePc = "0",
                presenceMobile = "0"
            ),
            isToggleable = true
        )
    }
}
