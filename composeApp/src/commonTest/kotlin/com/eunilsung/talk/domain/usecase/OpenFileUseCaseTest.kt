package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.OpenFileResult
import com.eunilsung.talk.domain.repository.DeviceFileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 받은 파일 열기 — 받아 둔 파일이 있으면 그대로 열고, 없으면 받아서 연다. */
class OpenFileUseCaseTest {

    private class FakeFiles(
        var downloaded: String? = null,
        var canDownload: Boolean = true,
        var canOpen: Boolean = true,
    ) : DeviceFileRepository {
        val opened = mutableListOf<String>()
        var downloadCount = 0

        override suspend fun saveToDownloads(source: String, displayName: String): String? {
            downloadCount++
            return if (canDownload) "/downloads/$displayName" else null
        }

        override suspend fun findDownloaded(displayName: String): String? = downloaded

        override suspend fun open(path: String): Boolean {
            opened += path
            return canOpen
        }

        override suspend fun saveToGallery(source: String, saveAs: String, isVideo: Boolean) = true
    }

    @Test
    fun 받아_둔_파일이_있으면_다시_받지_않고_연다() = runTest {
        val files = FakeFiles(downloaded = "/downloads/a.pdf")

        val result = OpenFileUseCase(files)("server-a", "a.pdf")

        assertEquals(OpenFileResult.OPENED, result)
        assertEquals(0, files.downloadCount)
        assertEquals(listOf("/downloads/a.pdf"), files.opened)
    }

    @Test
    fun 받아_둔_파일이_없으면_받아서_연다() = runTest {
        val files = FakeFiles()

        val result = OpenFileUseCase(files)("server-a", "a.pdf")

        assertEquals(OpenFileResult.DOWNLOADED_AND_OPENED, result)
        assertEquals(listOf("/downloads/a.pdf"), files.opened)
    }

    @Test
    fun 받지_못하면_열지_않고_실패한다() = runTest {
        val files = FakeFiles(canDownload = false)

        val result = OpenFileUseCase(files)("server-a", "a.pdf")

        assertEquals(OpenFileResult.FAILED, result)
        assertEquals(emptyList(), files.opened)
    }

    @Test
    fun 열_앱이_없으면_실패한다() = runTest {
        val files = FakeFiles(canOpen = false)

        assertEquals(OpenFileResult.FAILED, OpenFileUseCase(files)("server-a", "a.pdf"))
    }
}
