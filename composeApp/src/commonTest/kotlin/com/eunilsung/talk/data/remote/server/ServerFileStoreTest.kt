package com.eunilsung.talk.data.remote.server

import com.eunilsung.talk.testsupport.FakeFileMetadataResolver
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 서버 파일 보관소의 파일 id 찾기 — 방·대화로 적어 둔 서버 id 를 찾고, 그 기록이 없을 때 쓰는 기기 경로 거꾸로 찾기.
 */
class ServerFileStoreTest {

    private val settings = MapSettings()
    private val server = FakeTalkServer()
    private val store = ServerFileStore(settings, server, FakeFileMetadataResolver())

    @Test
    fun 적어_둔_경로로_서버_id_를_찾는다() {
        store.remember("file-a", "/cache/file-a_회의록.pdf")
        store.remember("file-b", "/device/보고서.docx")

        assertEquals("file-a", store.fileIdOf("/cache/file-a_회의록.pdf"))
        assertEquals("file-b", store.fileIdOf("/device/보고서.docx"))
    }

    @Test
    fun 적어_두지_않은_경로나_빈_경로는_null_이다() {
        store.remember("file-a", "/cache/a.pdf")

        assertNull(store.fileIdOf("/cache/b.pdf"))
        assertNull(store.fileIdOf(""))
    }

    @Test
    fun 다른_설정에_같은_값이_있어도_파일_기록만_본다() {
        settings.putString("last_shared_path", "/cache/a.pdf")

        assertNull(store.fileIdOf("/cache/a.pdf"))
    }

    @Test
    fun 한_경로를_여러_id_가_가리키면_경로로는_그중_하나다() {
        store.remember("file-a", "/device/a.pdf")
        store.remember("file-a-copy", "/device/a.pdf")

        assertTrue(store.fileIdOf("/device/a.pdf") in setOf("file-a", "file-a-copy"))
    }

    @Test
    fun 같은_경로를_두_방에_보내도_대화마다_그_방의_id_를_찾는다() {
        store.remember("file-x", "/device/a.pdf")
        store.remember("file-y", "/device/a.pdf")
        store.rememberChatFile("room-a", "chat-a", "file-x")
        store.rememberChatFile("room-b", "chat-b", "file-y")

        assertEquals("file-x", store.chatFileId("room-a", "chat-a"))
        assertEquals("file-y", store.chatFileId("room-b", "chat-b"))
    }

    @Test
    fun 대화_id_가_같아도_방이_다르면_따로_적는다() {
        store.rememberChatFile("room-a", "20261024101010000.test1", "file-x")
        store.rememberChatFile("room-b", "20261024101010000.test1", "file-y")

        assertEquals("file-x", store.chatFileId("room-a", "20261024101010000.test1"))
        assertEquals("file-y", store.chatFileId("room-b", "20261024101010000.test1"))
        assertNull(store.chatFileId("room-c", "20261024101010000.test1"))
    }

    @Test
    fun 적어_두지_않은_대화나_빈_값은_null_이고_빈_값은_적지_않는다() {
        store.rememberChatFile("room-a", "chat-a", "")
        store.rememberChatFile("", "chat-a", "file-x")
        store.rememberChatFile("room-a", "", "file-x")

        assertNull(store.chatFileId("room-a", "chat-a"))
        assertNull(store.chatFileId("", ""))
        assertTrue(settings.keys.isEmpty())
    }

    @Test
    fun 대화_기록은_경로_거꾸로_찾기에_섞이지_않는다() {
        store.rememberChatFile("room-a", "chat-a", "file-x")

        assertNull(store.fileIdOf("file-x"))
        assertNull(store.localPath("file-x"))
    }

    @Test
    fun 받은_파일_이름에_경로가_있어도_캐시_이름은_id_접두어와_마지막_조각이다() = runTest {
        server.uploads["abcdefgh-1234"] = byteArrayOf(7)

        val path = store.download("abcdefgh-1234", "../../Library/Preferences/abcd_target.plist")

        assertEquals("/cache/abcdefgh_abcd_target.plist", path)
    }
}
