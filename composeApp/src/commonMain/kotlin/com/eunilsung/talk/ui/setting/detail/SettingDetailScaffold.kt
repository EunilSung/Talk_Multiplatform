package com.eunilsung.talk.ui.setting.detail

import com.eunilsung.talk.ui.main.popToEmptyRoot
import com.eunilsung.talk.ui.uikit.topbar.BackScaffold
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_left_icon
import com.eunilsung.talk.ui.main.LocalRightNavigator
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SettingDetailScaffold(
    title: String,
    content: @Composable (PaddingValues) -> Unit
) {
    val rightNavigator = LocalRightNavigator.current
    BackScaffold(title = title, onBack = { rightNavigator.popToEmptyRoot() }, content = content)
}

@Composable
internal fun SettingDetailRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppColors.TextSub,
                    fontSize = 14.sp
                )
            )
        }
    }
}
