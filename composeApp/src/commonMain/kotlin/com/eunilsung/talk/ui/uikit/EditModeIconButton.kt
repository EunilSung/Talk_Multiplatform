package com.eunilsung.talk.ui.uikit

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.edit_mode_icon
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun EditModeIconButton(
    isEditMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        modifier = modifier.size(28.dp),
        onClick = onToggle,
    ) {
        Icon(
            painter = painterResource(Res.drawable.edit_mode_icon),
            contentDescription = "Edit Mode",
            modifier = Modifier.size(28.dp),
            tint = if (isEditMode) AppColors.PrimaryMain else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EditModeIconButtonPreview() {
    MaterialTheme {
        EditModeIconButton(isEditMode = true, onToggle = {})
    }
}
