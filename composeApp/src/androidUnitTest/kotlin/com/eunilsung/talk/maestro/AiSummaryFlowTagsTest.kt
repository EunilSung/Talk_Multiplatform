package com.eunilsung.talk.maestro

import com.eunilsung.talk.ui.uikit.TestTags
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * AI 요약 실화면 흐름(`.maestro/flows/06_ai_summary.yaml`)이 찾는 이름이 앱의 [TestTags] 와 맞는지 본다.
 *
 * 흐름은 기기에서만 돌아, 이름이 어긋나도 평소 테스트로는 알 수 없다. 그래서 흐름 파일을 읽어 두 가지를 지킨다.
 * - 안내 토스트는 문구가 아니라 태그로 찾고, 미설치와 받는 중을 다른 태그로 구분한다.
 * - AI Agent 가 열렸는지는 Talk 에만 있는 태그가 사라지고 Talk 에 없는(AI Agent 에만 있는) 태그가 보이는지로 본다.
 *   AI Agent 대화방에도 있는 `chat_input` 으로는 앱이 바뀌었는지 알 수 없다.
 */
class AiSummaryFlowTagsTest {

    private val flow = File("../.maestro/flows/06_ai_summary.yaml").readText()

    /** AI Agent 가 없는 기기의 흐름. */
    private val notInstalledBranch = flow.substringAfter(NOT_INSTALLED_MARKER).substringBefore(INSTALLED_MARKER)

    /** AI Agent 가 있는 기기의 흐름. */
    private val installedBranch = flow.substringAfter(INSTALLED_MARKER)

    /** 앱의 TestTags 상수 값 전부. */
    private val talkTags: Set<String> = TestTags::class.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { it.get(null) as String }
        .toSet()

    /** [key] (`visible`·`notVisible`) 바로 아래 `id:` 로 찾는 이름들. `a|b` 는 나눠 돌려준다. */
    private fun String.idsUnder(key: String): List<String> =
        Regex("""(?m)^\s*$key:\s*\n\s*id:\s*"?([^"\n]+)"?\s*$""").findAll(this)
            .flatMap { it.groupValues[1].trim().split('|') }
            .toList()

    @Test
    fun 흐름_파일에_두_갈래가_모두_있다() {
        assertTrue(NOT_INSTALLED_MARKER in flow && INSTALLED_MARKER in flow, "06_ai_summary.yaml 의 설치 여부 갈래를 찾지 못했다")
    }

    @Test
    fun 미설치_안내_토스트는_글자가_아니라_앱의_태그로_기다린다() {
        assertEquals(listOf(TestTags.TOAST_AI_AGENT_NOT_INSTALLED), notInstalledBranch.idsUnder("visible"))
        assertFalse("AI Agent.*\"" in flow, "토스트를 문구 정규식으로 찾고 있다")
    }

    @Test
    fun 미설치와_받는_중_토스트는_태그가_다르다() {
        assertNotEquals(TestTags.TOAST_AI_AGENT_NOT_INSTALLED, TestTags.TOAST_AI_SUMMARY_FILE_NOT_READY)
        assertTrue(TestTags.TOAST_AI_SUMMARY_FILE_NOT_READY !in notInstalledBranch.idsUnder("visible"))
    }

    @Test
    fun Talk_를_떠났는지는_AI_Agent_와_겹치지_않는_Talk_태그로_본다() {
        val gone = installedBranch.idsUnder("notVisible")

        assertEquals(listOf(TestTags.CHAT_ROOM_MENU), gone)
        assertTrue(gone.all { it in talkTags && it !in SHARED_WITH_AI_AGENT }, "AI Agent 에도 있는 태그로 판정한다: $gone")
    }

    @Test
    fun AI_Agent_가_열렸는지는_AI_Agent_에만_있는_태그가_보이는지로_본다() {
        val shown = installedBranch.idsUnder("visible")

        assertEquals(AI_AGENT_ONLY.sorted(), shown.sorted())
        assertTrue(shown.none { it in talkTags }, "Talk 에도 있는 태그가 섞여 있다: $shown")
    }

    private companion object {
        const val NOT_INSTALLED_MARKER = "AI_AGENT_INSTALLED !== 'true'}"
        const val INSTALLED_MARKER = "AI_AGENT_INSTALLED === 'true'}"

        /** AI Agent 저장소 `ui/uikit/TestTags.kt` 의 CHAT_BACK(대화방 뒤로)·DIALOG_TITLE(로그인 안내 팝업 제목). */
        val AI_AGENT_ONLY = setOf("chat_back", "dialog_title")

        /** Talk 와 AI Agent 가 같은 이름으로 쓰는 태그. 앱이 바뀌었는지 판정하는 데 쓰면 안 된다. */
        val SHARED_WITH_AI_AGENT = setOf("chat_input", "chat_send", "login_id", "login_pw", "login_submit", "dialog_confirm")
    }
}
