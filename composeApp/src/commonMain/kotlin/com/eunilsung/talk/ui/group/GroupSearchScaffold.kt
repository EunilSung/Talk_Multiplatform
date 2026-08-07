package com.eunilsung.talk.ui.group

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.group
import multiplatformtalk.composeapp.generated.resources.group_search_hint
import com.eunilsung.talk.ui.uikit.search.SearchBar
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSearchScaffold(
    listState: LazyListState,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    isShowTitle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val keyboardController = LocalSoftwareKeyboardController.current

    // heightOffset 을 key 로 쓰면 스크롤 프레임마다 화면 스코프가 무효화되고 코루틴도 재기동된다.
    // snapshotFlow 로 구독을 이 이펙트 안에 가두고, 임계값을 넘는 순간만 걸러낸다.
    LaunchedEffect(scrollBehavior) {
        snapshotFlow { scrollBehavior.state.heightOffset < TOP_BAR_COLLAPSE_THRESHOLD }
            .distinctUntilChanged()
            .filter { it }
            .collect { keyboardController?.hide() }
    }

    LaunchedEffect(searchQuery) {
        listState.scrollToItem(0)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBarV1(
                title = stringResource(Res.string.group),
                scrollBehavior = scrollBehavior,
                actions = actions,
                bottomContent = {
                    SearchBar(
                        searchTxt = searchQuery,
                        hintTxt = stringResource(Res.string.group_search_hint),
                        onSearchTextChanged = onSearchChange,
                        onClearClick = onClearSearch,
                        onSearchClick = { }
                    )
                },
                isShowTitle = isShowTitle
            )
        },
        content = content
    )
}

/** 상단바가 이만큼 접히면 스크롤 의도로 보고 키보드를 내린다. */
private const val TOP_BAR_COLLAPSE_THRESHOLD = -10f
