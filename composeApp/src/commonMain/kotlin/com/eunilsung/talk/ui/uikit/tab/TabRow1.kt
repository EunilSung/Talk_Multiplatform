package com.eunilsung.talk.ui.uikit.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors

data class TabItem1(
    val text: String,
    val isSelected: Boolean,
    val hasBadge: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun TabRow1(
    items: List<TabItem1>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selectedIndex = items.indexOfFirst { it.isSelected }.let { if (it < 0) 0 else it }

    TabRow(
        modifier = modifier,
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        contentColor = AppColors.PrimaryMain,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = AppColors.PrimaryMain,
                    height = 2.dp,
                )
            }
        },
        divider = {
            HorizontalDivider(thickness = 1.dp, color = AppColors.Line)
        },
    ) {
        items.forEach { item ->
            Tab(
                selected = item.isSelected,
                onClick = item.onClick,
                selectedContentColor = AppColors.PrimaryMain,
                unselectedContentColor = AppColors.TextSub,
                text = {
                    Box {
                        Text(
                            text = item.text,
                            fontSize = 14.sp,
                            fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (item.hasBadge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-3).dp)
                                    .size(6.dp)
                                    .background(AppColors.Red, CircleShape)
                            )
                        }
                    }
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TabRow1Preview() {
    MaterialTheme {
        TabRow1(
            items = listOf(
                TabItem1(text = "내 그룹", isSelected = true, hasBadge = true, onClick = {}),
                TabItem1(text = "대화함", isSelected = false, onClick = {}),
                TabItem1(text = "환경설정", isSelected = false, onClick = {}),
            )
        )
    }
}
