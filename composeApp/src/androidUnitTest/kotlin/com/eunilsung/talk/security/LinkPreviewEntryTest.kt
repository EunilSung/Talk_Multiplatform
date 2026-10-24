package com.eunilsung.talk.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 링크 미리보기 카드가 '내가 보낸 대화인지'를 그대로 ViewModel 에 넘기는지 본다.
 *
 * 받은 대화의 링크를 저절로 받지 않는 판정은 `LinkPreviewViewModel` 이 `isMe` 로 한다(`LinkPreviewViewModelTest`).
 * 화면이 이 값을 바꿔 넘기면(예: 언제나 true) 받은 링크를 누르기 전에 받아 와 보낸 사람이 받는 사람의 IP·열람 시각을 안다.
 * 화면 코드라 단위 테스트로 누를 수 없어 소스를 읽어 지킨다.
 */
class LinkPreviewEntryTest {

    private val sources = File("src/commonMain/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun 미리보기_카드는_말풍선만_부르고_보낸_사람_여부를_그대로_넘긴다() {
        assertTrue(sources.isNotEmpty(), "commonMain 소스를 찾지 못했다")
        val callers = sources.filter { file -> CARD_CALL.findAll(file.readText()).any() }.map { it.name }.sorted()
        assertEquals(listOf("TextItem.kt"), callers)

        val textItem = sources.single { it.name == "TextItem.kt" }.readText()
        assertTrue(CARD_CALL_WITH_IS_ME.containsMatchIn(textItem), "말풍선이 isMe = itemProps.isMe 를 넘기지 않는다")
    }

    @Test
    fun 미리보기_카드는_받은_값을_바꾸지_않고_ViewModel_에_넘긴다() {
        val card = sources.single { it.name == "LinkPreviewCard.kt" }.readText()
        assertTrue(SHOWN_WITH_IS_ME.containsMatchIn(card), "카드가 OnShown(url, isMe) 를 보내지 않는다")
        assertEquals(1, SHOWN_CALL.findAll(card).count(), "카드가 OnShown 을 여러 곳에서 보낸다")
    }

    private companion object {
        val CARD_CALL = Regex("""(?<!fun )\bLinkPreviewCard\(""")
        val CARD_CALL_WITH_IS_ME = Regex("""\bLinkPreviewCard\([^)]*\bisMe\s*=\s*itemProps\.isMe\s*\)""")
        val SHOWN_CALL = Regex("""LinkPreviewActions\.OnShown\(""")
        val SHOWN_WITH_IS_ME = Regex("""LinkPreviewActions\.OnShown\(\s*url\s*,\s*isMe\s*\)""")
    }
}
