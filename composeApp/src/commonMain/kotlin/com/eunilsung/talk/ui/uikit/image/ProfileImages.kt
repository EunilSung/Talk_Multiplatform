package com.eunilsung.talk.ui.uikit.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ProfileImages(
    userIds: List<String>,
    modifier: Modifier = Modifier.size(50.dp),
    roundDp: Dp? = null,
    onClick: (() -> Unit)? = null
) {
    val ids = userIds.take(4)
    val version = ProfileImageRefresh.version
    fun profileUrl(id: String): String = profileImageUrl(id, version)
    Box(modifier = modifier) {
        when (ids.size) {
            0 -> {
                UrlImage(
                    "",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(roundDp ?: 18.dp)).background(Color(0xFFDFE2E6)),
                    onClick = onClick
                )
            }
            1 -> {
                UrlImage(
                    url = profileUrl(ids[0]),
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(roundDp ?: 18.dp)).background(Color(0xFFDFE2E6)),
                    onClick = onClick
                )
            }
            2 -> {
                UrlImage(
                    url = profileUrl(ids[0]),
                    modifier = Modifier.size(32.dp).align(Alignment.TopStart).clip(RoundedCornerShape(12.dp)).background(Color(0xFFDFE2E6))
                )
                UrlImage(
                    url = profileUrl(ids[1]),
                    modifier = Modifier.size(32.dp).align(Alignment.BottomEnd).clip(RoundedCornerShape(12.dp)).background(Color(0xFFDFE2E6))
                )
            }
            3 -> {
                UrlImage(
                    url = profileUrl(ids[0]),
                    modifier = Modifier.size(26.dp).align(Alignment.TopCenter).clip(RoundedCornerShape(10.dp)).background(Color(0xFFDFE2E6))
                )
                UrlImage(
                    url = profileUrl(ids[1]),
                    modifier = Modifier.size(26.dp).align(Alignment.BottomStart).clip(RoundedCornerShape(10.dp)).background(Color(0xFFDFE2E6))
                )
                UrlImage(
                    url = profileUrl(ids[2]),
                    modifier = Modifier.size(26.dp).align(Alignment.BottomEnd).clip(RoundedCornerShape(10.dp)).background(Color(0xFFDFE2E6))
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        UrlImage(
                            url = profileUrl(ids[0]),
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDFE2E6))
                        )
                        UrlImage(
                            url = profileUrl(ids[1]),
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDFE2E6))
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        UrlImage(
                            url = profileUrl(ids[2]),
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDFE2E6))
                        )
                        UrlImage(
                            url = profileUrl(ids[3]),
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDFE2E6))
                        )
                    }
                }
            }
        }
    }
}
