package com.eunilsung.talk.ui.uikit.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable
import com.eunilsung.talk.ui.uikit.line.LineDivider

@Composable
fun DrawerSectionHeader(
    label: String,
    count: Int,
    trailingIcon: Painter? = null,
    trailingContentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label ($count)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = AppColors.Text,
                modifier = Modifier.weight(1f),
            )
            if (trailingIcon != null) {
                Icon(
                    painter = trailingIcon,
                    contentDescription = trailingContentDescription,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun DrawerSectionHeaderPreview() {
    MaterialTheme {
        DrawerSectionHeader(label = "참여자", count = 3)
    }
}