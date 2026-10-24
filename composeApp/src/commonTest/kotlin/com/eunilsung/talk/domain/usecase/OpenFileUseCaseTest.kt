package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.OpenFileResult
import com.eunilsung.talk.domain.repository.DeviceFileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 받은 파일 열기 — 앱 캐시의 사본으로만 열고, 공용 다운로드 폴더에는 '저장'을 눌렀을 때만 쓴다. */
class OpenFileUseCaseTest {

    private class FakeFiles(
        var canCopy: Boolean = true,
        var canOpen: Boolean = true,
    ) : DeviceFileRepository {
        val opened = mutableListOf<String>()
        val copied = mutableListOf<String>()
        var downloadCount = 0

        override suspend fun saveToDownloads(source: String, displayName: String): String? {
            downloadCount++
            return "/downloads/$displayName"
        }

        override suspend fun copyForOpen(source: String, displayName: String): String? {
            copied += displayName
            return if (canCopy) "/cache/open_files/$displayName" else null
        }

        override suspend fun open(path: String): Boolean {
            opened += path
            return canOpen
        }

        override suspend fun saveToGallery(source: String, saveAs: String, isVideo: Boolean) = true
    }

    @Test
    fun 열기는_캐시_사본으로만_열고_다운로드_폴더에_저장하지_않는다() = runTest {
        val files = FakeFiles()

        val result = OpenFileUseCase(files)("server-a", "a.pdf")

        assertEquals(OpenFileResult.OPENED, result)
        assertEquals(0, files.downloadCount)
        assertEquals(listOf("/cache/open_files/a.pdf"), files.opened)
    }

    @Test
    fun 저장은_다운로드_폴더에_쓴다() = runTest {
        val files = FakeFiles()

        val saved = DownloadFileUseCase(files)("server-a", "a.pdf")

        assertEquals(true, saved)
        assertEquals(1, files.downloadCount)
        assertEquals(emptyList(), files.opened)
    }

    @Test
    fun 사본을_만들지_못하면_열지_않고_실패한다() = runTest {
        val files = FakeFiles(canCopy = false)

        val result = OpenFileUseCase(files)("server-a", "a.pdf")

        assertEquals(OpenFileResult.FAILED, result)
        assertEquals(emptyList(), files.opened)
        assertEquals(0, files.downloadCount)
    }

    @Test
    fun 열_앱이_없으면_실패한다() = runTest {
        val files = FakeFiles(canOpen = false)

        assertEquals(OpenFileResult.FAILED, OpenFileUseCase(files)("server-a", "a.pdf"))
    }
}
