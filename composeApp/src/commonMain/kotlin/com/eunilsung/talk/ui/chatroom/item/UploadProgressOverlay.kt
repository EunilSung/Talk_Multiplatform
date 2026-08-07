package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.pointer.blockPointerInput

/**
 * 파일/이미지 송신 중 버블 전체를 덮는 업로드 진행률 오버레이.
 *
 * @param progress 0..100 진행률.
 * @param shape 버블 모양 — 스크림을 버블 모서리에 맞춰 clip.
 */
@Composable
fun UploadProgressOverlay(
    progress: Int,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        // 업로드 중에는 말풍선 탭이 통하면 안 된다 — 아직 서버에 없는 이미지의 뷰어가 열린다.
        modifier = modifier
            .clip(shape)
            .background(AppColors.Black.copy(alpha = 0.4f))
            .blockPointerInput(),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { (progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.size(40.dp),
                color = AppColors.White,
                trackColor = AppColors.White.copy(alpha = 0.3f),
                strokeWidth = 3.dp,
            )
            Text(
                text = "$progress%",
                color = AppColors.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
