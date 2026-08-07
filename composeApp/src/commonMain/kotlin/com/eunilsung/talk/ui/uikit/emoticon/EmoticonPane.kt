package com.eunilsung.talk.ui.uikit.emoticon

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.allDrawableResources
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_face_human_down
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_four_letter_down
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_four_letter_up
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_hand_ani_down
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_hand_ani_up
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_word_ani_down
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_word_ani_up
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_social_life_down
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_social_life_up
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

class EmoticonPanelController {
    var isOpen by mutableStateOf(false)
        private set

    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    var isExpanded by mutableStateOf(false)
        private set

    var collapseSignal by mutableStateOf(0)
        private set

    fun open(content: @Composable () -> Unit) {
        this.content = content
        isOpen = true
        isExpanded = false
    }

    fun close() {
        isOpen = false
        isExpanded = false
    }

    fun markExpanded(expanded: Boolean) {
        isExpanded = expanded
    }

    fun collapse() {
        isExpanded = false
        collapseSignal++
    }
}

val LocalEmoticonPanel = compositionLocalOf<EmoticonPanelController?> { null }

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun rememberStableImeHeight(
    currentImeHeight: Dp,
    fallback: Dp = 300.dp,
): MutableState<Dp> {
    val current = rememberUpdatedState(currentImeHeight)
    val state = remember { mutableStateOf(fallback) }
    LaunchedEffect(Unit) {
        var sessionMax = 0.dp
        launch {
            snapshotFlow { current.value }.collect { v ->
                if (v <= 0.dp) sessionMax = 0.dp
                else if (v > sessionMax) sessionMax = v
            }
        }
        snapshotFlow { current.value }
            .debounce(100)
            .collect { if (it > 0.dp && it >= sessionMax) state.value = it }
    }
    return state
}

private val EmoticonTabBarHeight = 42.dp
private val EmoticonTabItemSize = 42.dp
private val EmoticonTabImageSize = 42.dp

private data class EmoticonTab(
    val word: String,
    val unselected: DrawableResource,
    val selected: DrawableResource,
)

private val emoticonTabs: List<EmoticonTab> = listOf(
    EmoticonTab("word_ani", Res.drawable.emoticon_tab_word_ani_up, Res.drawable.emoticon_tab_word_ani_down),
    EmoticonTab("four_letter", Res.drawable.emoticon_tab_four_letter_up, Res.drawable.emoticon_tab_four_letter_down),
    EmoticonTab("social_life", Res.drawable.emoticon_tab_social_life_up, Res.drawable.emoticon_tab_social_life_down),
    EmoticonTab("hand_ani", Res.drawable.emoticon_tab_hand_ani_up, Res.drawable.emoticon_tab_hand_ani_down),
)

@Composable
fun EmoticonPaneContent(
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    onEmoticonSelected: (EmoticonItem) -> Unit = {},
    onEmoticonDoubleClick: ((EmoticonItem) -> Unit)? = null,
    hideAnimatedTabs: Boolean = false,
) {
    val tabs = remember(hideAnimatedTabs) {
        if (hideAnimatedTabs) emoticonTabs.filterNot { it.word.endsWith("_ani") } else emoticonTabs
    }

    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size },
    )
    val tabListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val selectedTab = pagerState.currentPage

    LaunchedEffect(selectedTab) { onTabSelected(selectedTab) }

    LaunchedEffect(selectedTab) {
        val info = tabListState.layoutInfo
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        if (viewport > 0) {
            val itemW = with(density) { EmoticonTabItemSize.toPx() }.toInt()
            tabListState.animateScrollToItem(selectedTab, -((viewport - itemW) / 2))
        } else {
            tabListState.animateScrollToItem(selectedTab)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {

        LazyRow(
            state = tabListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(EmoticonTabBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.size(8.dp)) }
            items(tabs.size) { index ->
                val selected = index == selectedTab
                val tab = tabs[index]
                Box(
                    modifier = Modifier
                        .size(EmoticonTabItemSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { scope.launch { pagerState.animateScrollToPage(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(if (selected) tab.selected else tab.unselected),
                        contentDescription = "emoticon tab $index",
                        modifier = Modifier.size(EmoticonTabImageSize),
                    )
                }
            }
            item { Spacer(modifier = Modifier.size(8.dp)) }
        }

        Spacer(modifier = Modifier.size(8.dp))

        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            EmoticonGrid(
                tab = tabs[page],
                onEmoticonClick = onEmoticonSelected,
                onEmoticonDoubleClick = onEmoticonDoubleClick,
            )
        }
    }
}

private val emoticonIdToResourceName: Map<String, String> by lazy {
    emoticonLegacyIds.entries.associate { (resName, id) -> id to resName }
}

fun emoticonResourceForId(id: String): DrawableResource? {
    if (id.isBlank()) return null
    val resName = emoticonIdToResourceName[id] ?: return null
    return Res.allDrawableResources[resName]
}

fun emoticonResourceNameForId(id: String): String? {
    if (id.isBlank()) return null
    return emoticonIdToResourceName[id]
}

fun isAnimatedEmoticonName(resourceName: String): Boolean = resourceName.contains("_ani_")

val EmoticonItem.resourceName: String
    get() = resourcePath.removePrefix("drawable/").removeSuffix(".png")

val EmoticonItem.isAnimated: Boolean
    get() = isAnimatedEmoticonName(resourceName)


private val emoticonImgTagRegex = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val emoticonFileAttrRegex = Regex("EmoticonFile\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
private val emoticonSrcAttrRegex = Regex("\\s+src\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE)

private fun emoticonResourcePathForId(id: String): String? =
    emoticonIdToResourceName[id]?.let { "drawable/$it.png" }

@OptIn(ExperimentalEncodingApi::class)
suspend fun resolveEmoticonImages(html: String): String {
    if (html.isBlank() || !html.contains("EmoticonFile", ignoreCase = true)) return html

    val ids = emoticonFileAttrRegex.findAll(html)
        .map { it.groupValues[1] }
        .filter { it.isNotBlank() }
        .toSet()
    if (ids.isEmpty()) return html

    val uriById = HashMap<String, String>()
    for (id in ids) {
        val path = emoticonResourcePathForId(id) ?: continue
        runCatching {
            val bytes = Res.readBytes(path)
            uriById[id] = "data:image/png;base64," + Base64.encode(bytes)
        }
    }
    if (uriById.isEmpty()) return html

    return emoticonImgTagRegex.replace(html) { m ->
        val tag = m.value
        val id = emoticonFileAttrRegex.find(tag)?.groupValues?.get(1) ?: return@replace tag
        val uri = uriById[id] ?: return@replace tag
        val noSrc = emoticonSrcAttrRegex.replace(tag, "")
        noSrc.replaceFirst(Regex("<img", RegexOption.IGNORE_CASE), "<img src=\"$uri\"")
    }
}

private fun emoticonsForTab(word: String): List<EmoticonItem> {
    val prefix = if (word == "basic") "emoticon_" else "emoticon_${word}_"
    return Res.allDrawableResources
        .entries
        .filter { it.key.startsWith(prefix) && it.key.removePrefix(prefix).all(Char::isDigit) }
        .sortedBy { it.key }
        .map { (name, res) ->
            EmoticonItem(
                id = emoticonLegacyIds[name] ?: name,
                resource = res,
                resourcePath = "drawable/$name.png",
            )
        }
}

private val EmoticonItemSize = 80.dp
private const val EmoticonSampleItemCount = 24

data class EmoticonItem(
    val id: String,
    val resource: DrawableResource,
    val resourcePath: String,
)

@Composable
private fun EmoticonGrid(
    tab: EmoticonTab,
    onEmoticonClick: (EmoticonItem) -> Unit = {},
    onEmoticonDoubleClick: ((EmoticonItem) -> Unit)? = null,
) {
    val items = remember(tab.word) { emoticonsForTab(tab.word) }
    val sample = EmoticonItem(
        id = "sample_${tab.word}",
        resource = Res.drawable.emoticon_tab_face_human_down,
        resourcePath = "drawable/emoticon_tab_face_human_down.png",
    )
    val display = items.ifEmpty { List(EmoticonSampleItemCount) { sample } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = EmoticonItemSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 50.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(display.size) { index ->
            val item = display[index]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onEmoticonClick(item) },
                        onDoubleClick = onEmoticonDoubleClick?.let { cb -> { cb(item) } },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                StaticEmoticonImage(
                    resourceName = item.resourceName,
                    contentDescription = item.id,
                    modifier = Modifier.size(EmoticonItemSize),
                )
            }
        }
    }
}
