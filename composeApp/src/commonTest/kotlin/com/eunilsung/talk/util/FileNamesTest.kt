package com.eunilsung.talk.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 받은 파일 이름 정리 — 보낸 사람이 정한 이름에 `../` 가 있어도 저장 폴더 밖(앱 DB·설정·다른 첨부 캐시)에 쓰지 않는다.
 */
class FileNamesTest {

    @Test
    fun 경로를_거슬러_올라가는_이름은_마지막_조각만_남긴다() {
        assertEquals("target.plist", sanitizeFileName("../../Library/Preferences/target.plist"))
        assertEquals("app.db", sanitizeFileName("..\\..\\databases\\app.db"))
        assertEquals("x.txt", sanitizeFileName("/etc/x.txt"))
    }

    @Test
    fun 점뿐이거나_빈_이름은_대신_쓰는_이름이_된다() {
        assertEquals(FALLBACK_FILE_NAME, sanitizeFileName(".."))
        assertEquals(FALLBACK_FILE_NAME, sanitizeFileName("."))
        assertEquals(FALLBACK_FILE_NAME, sanitizeFileName(""))
        assertEquals(FALLBACK_FILE_NAME, sanitizeFileName("a/"))
        assertEquals(FALLBACK_FILE_NAME, sanitizeFileName("../.."))
        assertEquals("다른이름", sanitizeFileName("..", fallback = "다른이름"))
    }

    @Test
    fun 제어_문자와_글자_방향_문자는_바꾼다() {
        assertEquals("a_b.txt", sanitizeFileName("a\u0000b.txt"))
        assertEquals("a_b.txt", sanitizeFileName("a\nb.txt"))
        assertEquals("invoice_fdp.exe", sanitizeFileName("invoice‮fdp.exe"))
    }

    @Test
    fun 평범한_이름은_그대로_둔다() {
        assertEquals("회의록 2026.pdf", sanitizeFileName("회의록 2026.pdf"))
        assertEquals("report..final.docx", sanitizeFileName("report..final.docx"))
        assertEquals(".hidden", sanitizeFileName(".hidden"))
    }

    @Test
    fun 저장_경로는_늘_폴더_안이다() {
        val dir = "/docs/MultiplatformTalk"
        assertEquals("$dir/target.plist", childPathInside(dir, "../../Library/Preferences/target.plist"))
        assertEquals("$dir/file", childPathInside(dir, ".."))
        assertEquals("$dir/a.pdf", childPathInside("$dir/", "a.pdf"))
        assertEquals("$dir/a.pdf", childPathInside("/docs/./x/../MultiplatformTalk", "a.pdf"))
    }

    @Test
    fun 폴더가_비면_경로도_없다() {
        assertNull(childPathInside("", "a.pdf"))
    }
}
