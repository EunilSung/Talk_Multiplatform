package com.eunilsung.talk.ui.userprofile.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.profile_chat
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.itemClickable
import org.jetbrains.compose.resources.painterResource

@Composable
fun ActionButton(
    label: String,
    image: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .itemClickable(cornerRadius = 12.dp, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = image,
            contentDescription = "Image",
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = AppColors.Text,
            fontSize = 13.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonPreview() {
    MaterialTheme {
        ActionButton(
            label = "대화하기",
            image = painterResource(Res.drawable.profile_chat),
            onClick = {},
        )
    }
}
