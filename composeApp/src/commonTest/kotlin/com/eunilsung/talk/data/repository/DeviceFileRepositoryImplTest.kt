package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.local.FileOpener
import com.eunilsung.talk.testsupport.FakeFileMetadataResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 열기용 사본 — 앱 cache 의 열기 폴더에만 쓰고, 원본마다 다른 폴더라 이름이 같아도 덮어쓰지 않는다. */
class DeviceFileRepositoryImplTest {

    private val resolver = FakeFileMetadataResolver()
    private val repository = DeviceFileRepositoryImpl(resolver, object : FileOpener {
        override suspend fun open(path: String) = true
    })

    @Test
    fun 열기용_사본은_열기_폴더에_원본_내용으로_쓴다() = runTest {
        resolver.put("/cache/chat_uploads/abcd1234_a.pdf", byteArrayOf(1, 2, 3))

        val path = assertNotNull(repository.copyForOpen("/cache/chat_uploads/abcd1234_a.pdf", "a.pdf"))

        assertTrue(path.startsWith("/cache/open_files/"), path)
        assertTrue(path.endsWith("/a.pdf"), path)
        assertEquals(listOf<Byte>(1, 2, 3), resolver.files.getValue(path).toList())
    }

    @Test
    fun 이름이_같아도_원본이_다르면_다른_사본이다() = runTest {
        resolver.put("/cache/chat_uploads/aaaa_a.pdf", byteArrayOf(1))
        resolver.put("/cache/chat_uploads/bbbb_a.pdf", byteArrayOf(2))

        val first = repository.copyForOpen("/cache/chat_uploads/aaaa_a.pdf", "a.pdf")
        val second = repository.copyForOpen("/cache/chat_uploads/bbbb_a.pdf", "a.pdf")

        assertNotEquals(first, second)
    }

    @Test
    fun 이름이_비면_원본_경로의_마지막_조각을_쓴다() = runTest {
        resolver.put("/cache/chat_uploads/aaaa_report.docx", byteArrayOf(1))

        val path = assertNotNull(repository.copyForOpen("/cache/chat_uploads/aaaa_report.docx", ""))

        assertTrue(path.endsWith("/aaaa_report.docx"), path)
    }

    @Test
    fun 원본을_읽지_못하면_사본도_없다() = runTest {
        assertNull(repository.copyForOpen("/cache/none.pdf", "none.pdf"))
    }
}
