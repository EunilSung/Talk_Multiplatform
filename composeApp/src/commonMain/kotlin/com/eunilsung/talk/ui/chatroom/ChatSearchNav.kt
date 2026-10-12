package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.Config
import com.eunilsung.talk.ui.theme.AppColors
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_down_icon
import multiplatformtalk.composeapp.generated.resources.arrow_up_icon
import multiplatformtalk.composeapp.generated.resources.search_date_icon
import multiplatformtalk.composeapp.generated.resources.search_user_icon
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ChatSearchNav(
    searchState: ChatSearchState,
    onUserClick: () -> Unit = {},
    onDateClick: () -> Unit = {},
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val total = searchState.totalMatches
        val pos = searchState.currentPosition

        if (Config.ChatRoom.IS_SEARCH_USER_ENABLED) {
            IconButton(
                modifier = Modifier.size(30.dp),
                onClick = onUserClick,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.search_user_icon),
                    modifier = Modifier.size(24.dp),
                    contentDescription = "Search user",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        if (Config.ChatRoom.IS_SEARCH_DATE_ENABLED) {
            IconButton(
                modifier = Modifier.size(30.dp),
                onClick = onDateClick,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.search_date_icon),
                    modifier = Modifier.size(24.dp),
                    contentDescription = "Search date",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = if (total == 0) "0" else "$pos / $total",
            color = AppColors.Text,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))

        IconButton(
            modifier = Modifier.size(24.dp),
            onClick = onPrevClick,
            enabled = total > 0 && searchState.currentIndex > 0
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_up_icon),
                modifier = Modifier.size(24.dp),
                contentDescription = "Prev match",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        IconButton(
            modifier = Modifier.size(24.dp),
            onClick = onNextClick,
            enabled = total > 0 && searchState.currentIndex < total - 1
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_down_icon),
                modifier = Modifier.size(24.dp),
                contentDescription = "Next match",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
