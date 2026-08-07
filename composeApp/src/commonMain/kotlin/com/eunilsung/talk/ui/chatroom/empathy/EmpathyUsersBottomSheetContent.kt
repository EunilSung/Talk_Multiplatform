package com.eunilsung.talk.ui.chatroom.empathy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.empathy_0
import multiplatformtalk.composeapp.generated.resources.empathy_1
import multiplatformtalk.composeapp.generated.resources.empathy_2
import multiplatformtalk.composeapp.generated.resources.empathy_3
import multiplatformtalk.composeapp.generated.resources.empathy_4
import multiplatformtalk.composeapp.generated.resources.empathy_5
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import multiplatformtalk.composeapp.generated.resources.all
import multiplatformtalk.composeapp.generated.resources.empathy_list_title
import multiplatformtalk.composeapp.generated.resources.empathy_empty
import com.eunilsung.talk.domain.model.EmpathyChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.itemClickable
import com.eunilsung.talk.ui.uikit.image.ProfileImages
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.ui.uikit.user.UserItemV3
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val EMPATHY_ICONS = listOf(
    Res.drawable.empathy_0, Res.drawable.empathy_1, Res.drawable.empathy_2,
    Res.drawable.empathy_3, Res.drawable.empathy_4, Res.drawable.empathy_5,
)

private const val PAGE_SIZE = 20

/** 공감 사용자 1명 + 그가 누른 공감 타입(0~5). */
private data class EmpathyEntry(val user: User, val typeIndex: Int)

/** 공감한 사용자 리스트 — BottomSheet 콘텐츠. */
@Composable
fun EmpathyUsersBottomSheetContent(
    empathy: EmpathyChat,
    selectedType: Int,
    resolveUser: (String) -> User? = { null },
    onUserClick: (User) -> Unit = {},
) {
    val buckets = remember(empathy) {
        listOf(empathy.empathy0, empathy.empathy1, empathy.empathy2,
            empathy.empathy3, empathy.empathy4, empathy.empathy5)
    }
    val allUsers = remember(empathy) { buckets.flatten() }
    val allEntries = remember(empathy) {
        buckets.flatMapIndexed { idx, list -> list.map { EmpathyEntry(it, idx) } }
    }
    val tabs = remember(empathy) {
        listOf(-1) + buckets.withIndex().filter { it.value.isNotEmpty() }.map { it.index }
    }

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(selectedType).coerceAtLeast(0),
        pageCount = { tabs.size },
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.empathy_list_title),
                color = AppColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            tabs.forEachIndexed { index, type ->
                EmpathyTab(
                    selected = pagerState.currentPage == index,
                    label = if (type < 0) stringResource(Res.string.all) else null,
                    iconIndex = if (type < 0) null else type,
                    count = if (type < 0) allUsers.size else buckets[type].size,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        }
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(420.dp),
        ) { page ->
            val type = tabs[page]
            val entries = if (type < 0) allEntries else allEntries.filter { it.typeIndex == type }
            EmpathyUserPage(
                entries = entries,
                resolveUser = resolveUser,
                onUserClick = onUserClick,
            )
        }
    }
}

/** 한 탭(전체/특정 타입)의 사용자 리스트 — 클라이언트 페이징. */
@Composable
private fun EmpathyUserPage(
    entries: List<EmpathyEntry>,
    resolveUser: (String) -> User?,
    onUserClick: (User) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(Res.string.empathy_empty), color = AppColors.TextSub, fontSize = 13.sp)
        }
        return
    }

    var visibleCount by remember(entries) { mutableIntStateOf(PAGE_SIZE) }
    val visibleEntries = remember(entries, visibleCount) { entries.take(visibleCount) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, entries) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (last != null && total > 0 && last >= total - 3 && visibleCount < entries.size) {
                    visibleCount = (visibleCount + PAGE_SIZE).coerceAtMost(entries.size)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Spacer(modifier = Modifier.height(10.dp)) }
        items(visibleEntries, key = { it.user.id }) { entry ->
            val resolved = resolveUser(entry.user.id) ?: entry.user
            EmpathyUserItem(
                user = resolved,
                typeIndex = entry.typeIndex,
                onClick = { onUserClick(resolved) },
            )
        }
    }
}

@Composable
private fun EmpathyUserItem(
    user: User,
    typeIndex: Int,
    onClick: () -> Unit = {},
) {
    val subtitle = listOfNotNull(
        user.departmentName?.takeIf { it.isNotBlank() },
        user.positionName?.takeIf { it.isNotBlank() },
    ).joinToString("/")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserItemV3(
            userId = user.id,
            name = user.name,
            subtitle = subtitle,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )

        EMPATHY_ICONS.getOrNull(typeIndex)?.let { icon ->
            Spacer(modifier = Modifier.width(10.dp))
            Image(
                painter = painterResource(icon),
                contentDescription = "empathy $typeIndex",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmpathyTab(
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    iconIndex: Int? = null,
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = 60.dp)
            .height(50.dp)
            .itemClickable(cornerRadius = 0.dp, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconIndex != null) {
                Image(
                    painter = painterResource(EMPATHY_ICONS[iconIndex]),
                    contentDescription = "empathy $iconIndex",
                    modifier = Modifier.size(20.dp),
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    color = AppColors.Text,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) AppColors.Text else Color.Transparent),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmpathyUsersBottomSheetContentPreview() {
    MaterialTheme {
        EmpathyUsersBottomSheetContent(
            empathy = EmpathyChat(
                chatID = "1",
                empathy0 = listOf(User(id = "1", name = "사용자1", departmentName = "개발팀")),
                empathy1 = listOf(User(id = "2", name = "사용자2")),
            ),
            selectedType = 0,
        )
    }
}
