package com.eunilsung.talk.data.local

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eunilsung.talk.util.Log
import platform.Foundation.NSURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** iOS [FileOpener] — 다운로드된 파일을 앱 내부 QuickLook 미리보기로 표시. */
class IosFileOpener : FileOpener {

    private var retainedDataSource: QLPreviewControllerDataSourceProtocol? = null

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun open(path: String): Boolean = withContext(Dispatchers.Main) {
        if (path.isBlank()) return@withContext false
        runCatching {
            val absolute = (if (path.startsWith("file://")) {
                NSURL.URLWithString(path)?.path ?: path.removePrefix("file://")
            } else path).existingFilePathOrSelf()
            val fileUrl = NSURL.fileURLWithPath(absolute)

            val presenter = topViewController() ?: run {
                Log.message("[FileOpener/iOS] no presenter VC")
                return@runCatching false
            }

            val dataSource = FilePreviewDataSource(fileUrl)
            retainedDataSource = dataSource

            val preview = QLPreviewController()
            preview.dataSource = dataSource
            presenter.presentViewController(preview, animated = true, completion = null)
            true
        }.getOrElse {
            Log.message("[FileOpener/iOS] preview failed: ${it.message}")
            false
        }
    }

    /** 현재 최상단(모달까지 포함) ViewController — 미리보기를 띄울 대상. */
    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        return top
    }
}

/** QuickLook 데이터소스 — 단일 파일 미리보기. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class FilePreviewDataSource(
    private val url: NSURL,
) : NSObject(), QLPreviewControllerDataSourceProtocol {

    private val item = FilePreviewItem(url)

    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): Long = 1

    override fun previewController(
        controller: QLPreviewController,
        previewItemAtIndex: Long,
    ): QLPreviewItemProtocol = item
}

/** QuickLook 미리보기 아이템 — 파일 URL + 표시 제목. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class FilePreviewItem(
    private val url: NSURL,
) : NSObject(), QLPreviewItemProtocol {

    override fun previewItemURL(): NSURL = url

    override fun previewItemTitle(): String? = url.lastPathComponent
}
