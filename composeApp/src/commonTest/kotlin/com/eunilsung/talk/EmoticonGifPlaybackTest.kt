package com.eunilsung.talk

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import com.eunilsung.talk.ui.uikit.emoticon.playGifFrames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 움직이는 이모티콘을 정한 횟수만큼 돌리고 마지막 장면에서 멈추는지 — iOS 는 GIF 프레임을 직접 돌리므로 이 순서가 곧 화면이다.
 *
 * 샘플 GIF 는 모두 파일에 "무한 반복" 으로 적혀 있어, 앱이 횟수를 정하지 않으면 대화방 말풍선이 끝없이 움직였다.
 */
class EmoticonGifPlaybackTest {

    @Test
    fun 세_번_돌고_마지막_장면에서_멈춘다() = runTest {
        val shown = mutableListOf<Int>()

        playGifFrames(listOf(100, 100), playCount = 3) { shown += it }

        assertEquals(listOf(0, 1, 0, 1, 0, 1), shown)
        assertEquals(600, currentTime, "마지막 장면을 제 시간만큼 보여 준 뒤 끝나야 한다")
    }

    @Test
    fun 횟수가_0이면_멈추지_않는다() = runTest {
        var shownCount = 0
        val play = launch { playGifFrames(listOf(100, 100), playCount = 0) { shownCount++ } }

        advanceTimeBy(1_050)

        assertTrue(play.isActive, "0 인데 멈췄다")
        assertEquals(11, shownCount)
        play.cancel()
    }

    @Test
    fun 한_장짜리는_돌리지_않는다() = runTest {
        val shown = mutableListOf<Int>()

        playGifFrames(listOf(100), playCount = 3) { shown += it }

        assertEquals(emptyList(), shown)
        assertEquals(0, currentTime)
    }
}
