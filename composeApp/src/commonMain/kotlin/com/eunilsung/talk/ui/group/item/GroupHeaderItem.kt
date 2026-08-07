package com.eunilsung.talk.ui.group.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.*
import com.eunilsung.talk.domain.model.isMyProfileGroup
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.isDefaultGroup
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.itemClickable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GroupHeaderItem(
    item: Group.Item,
    onExpandClick: () -> Unit,
    onOptionClick: (() -> Unit)? = null
) {
    val displayName = when {
        item.isMyProfileGroup -> stringResource(Res.string.my_profile)
        item.isDefaultGroup -> stringResource(Res.string.default_group)
        else -> item.name
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Gray50Bg)
            .itemClickable(onClick = onExpandClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(if (item.isExpanded) Res.drawable.arrow_up_icon else Res.drawable.arrow_down_icon),
            contentDescription = if (item.isExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(20.dp),
            tint = AppColors.TextSub
        )

        Spacer(modifier = Modifier.size(10.dp))

        Text(
            text = "$displayName (${item.userData.size})",
            modifier = Modifier.weight(1f),
            color = AppColors.TextSub,
            fontSize = 13.sp
        )

        if (onOptionClick != null) {
            IconButton(
                onClick = { onOptionClick.invoke() },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.vertical_ellipsis_icon),
                    contentDescription = "Group Options",
                    tint = AppColors.TextSub
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GroupHeaderItemPreview() {
    MaterialTheme {
        GroupHeaderItem(
            item = Group.Item(
                id = "1",
                name = "그룹1",
                alineCode = "1",
                userData = listOf(Group.User(userId = "1", userName = "Eunil Sung")),
                isExpanded = true
            ),
            onExpandClick = {},
            onOptionClick = {}
        )
    }
}
