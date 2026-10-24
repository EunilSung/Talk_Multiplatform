package com.eunilsung.talk.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 받아서 설치까지 하는 열기(`ExternalUrlOpener.openUpdate`)를 강제 업데이트 안내만 부르는지 본다.
 *
 * 대화 링크·미리보기 카드는 누구나 보낼 수 있는 주소라 브라우저로만 연다(`open`). 그쪽에서 `openUpdate` 를 부르면
 * 대화로 받은 `.apk` 가 'Talk 업데이트' 이름으로 내려받아져 설치 화면이 뜬다. 화면 코드라 단위 테스트로 누를 수 없어
 * 소스를 읽어 지킨다.
 */
class UpdateInstallEntryTest {

    private val sources = File("src/commonMain/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun 설치까지_하는_열기는_로그인_화면의_강제_업데이트만_부른다() {
        assertTrue(sources.isNotEmpty(), "commonMain 소스를 찾지 못했다")
        val callers = sources.filter { CALL.containsMatchIn(it.readText()) }.map { it.name }.sorted()
        assertEquals(listOf("LoginScreen.kt"), callers)
    }

    @Test
    fun 링크_미리보기_카드는_브라우저_열기만_부른다() {
        val card = sources.single { it.name == "LinkPreviewCard.kt" }.readText()
        assertTrue("urlOpener.open(url)" in card, "미리보기 카드가 브라우저 열기를 부르지 않는다")
    }

    private companion object {
        val CALL = Regex("""\.openUpdate\(""")
    }
}
