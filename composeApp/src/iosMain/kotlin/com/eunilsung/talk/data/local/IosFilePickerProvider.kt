package com.eunilsung.talk.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.eunilsung.talk.util.Log
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSFileManager
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

/** iOS [FilePickerProvider] — `UIDocumentPickerViewController` 위임. */
class IosFilePickerProvider : FilePickerProvider {

    private val _results = MutableSharedFlow<List<PickedFile>>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    override val results: SharedFlow<List<PickedFile>> = _results.asSharedFlow()

    /** delegate 참조 유지 — picker 가 dismiss 될 때까지 GC 방지. */
    private var currentDelegate: DocumentPickerDelegate? = null

    override var lastRequestId: String = ""
        private set

    @OptIn(ExperimentalForeignApi::class)
    override fun launchFilePicker(requestId: String, allowMultiple: Boolean, mimeTypes: List<String>) {
        lastRequestId = requestId
        val root = topMostViewController() ?: run {
            Log.message("[FilePicker] iOS — no root view controller")
            return
        }

        val types = listOf<UTType>(UTTypeData, UTTypeContent)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        picker.allowsMultipleSelection = allowMultiple

        val delegate = DocumentPickerDelegate { urls ->
            val files = urls.mapNotNull { url ->
                val rawPath = url.path ?: return@mapNotNull null
                val size = fileSize(rawPath)
                PickedFile(
                    uri = rawPath.toNfc(),
                    name = (url.lastPathComponent ?: "file").toNfc(),
                    sizeBytes = size,
                )
            }
            Log.message("[FilePicker] iOS picked ${files.size} files")
            _results.tryEmit(files)
            currentDelegate = null
        }
        currentDelegate = delegate
        picker.delegate = delegate

        root.presentViewController(picker, animated = true, completion = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fileSize(path: String): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
            ?: return -1L
        val sizeAny = attrs[platform.Foundation.NSFileSize] ?: return -1L
        return (sizeAny as? platform.Foundation.NSNumber)?.longLongValue ?: -1L
    }

    private fun topMostViewController(): UIViewController? {
        @Suppress("DEPRECATION")
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }
}

/** UIDocumentPickerDelegate Kotlin/Native 어댑터. */
private class DocumentPickerDelegate(
    private val onPicked: (List<NSURL>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    @Suppress("UNCHECKED_CAST")
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
        onPicked(urls)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(emptyList())
    }
}
