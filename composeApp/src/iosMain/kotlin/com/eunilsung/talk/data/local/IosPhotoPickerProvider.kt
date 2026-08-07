package com.eunilsung.talk.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import com.eunilsung.talk.util.Log
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** iOS [PhotoPickerProvider] — `PHPickerViewController`. */
class IosPhotoPickerProvider : PhotoPickerProvider {

    private val _results = MutableSharedFlow<List<PickedPhoto>>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    override val results: SharedFlow<List<PickedPhoto>> = _results.asSharedFlow()

    /** picker delegate / 비동기 로딩용 long-lived 스코프. */
    private val loaderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** delegate 참조 유지 — picker 가 dismiss 될 때까지 GC 방지. */
    private var currentDelegate: PhotoPickerDelegate? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun launchPhotoPicker(allowMultiple: Boolean, maxItems: Int) {
        val root = topMostViewController() ?: run {
            Log.message("[PhotoPicker] iOS — no root view controller")
            return
        }

        val config = PHPickerConfiguration().apply {
            selectionLimit = if (allowMultiple) maxItems.toLong() else 1L
            filter = PHPickerFilter.imagesFilter
        }
        val picker = PHPickerViewController(configuration = config)

        val delegate = PhotoPickerDelegate { pickerVc, pickerResults ->
            pickerVc.dismissViewControllerAnimated(true, completion = null)
            currentDelegate = null

            if (pickerResults.isEmpty()) {
                _results.tryEmit(emptyList())
                return@PhotoPickerDelegate
            }

            loaderScope.launch {
                val photos = pickerResults.map { result ->
                    async { copyToTempFile(result) }
                }.awaitAll().filterNotNull()
                Log.message("[PhotoPicker] iOS picked ${photos.size} / ${pickerResults.size} loaded")
                _results.tryEmit(photos)
            }
        }
        currentDelegate = delegate
        picker.delegate = delegate

        root.presentViewController(picker, animated = true, completion = null)
    }

    /** `PHPickerResult.itemProvider` → temp 파일 복사 → [PickedPhoto]. */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun copyToTempFile(result: PHPickerResult): PickedPhoto? =
        suspendCancellableCoroutine { cont ->
            val typeId = "public.image"
            result.itemProvider.loadFileRepresentationForTypeIdentifier(typeId) { srcUrl: NSURL?, _: NSError? ->
                if (srcUrl == null) {
                    if (cont.isActive) cont.resume(null)
                    return@loadFileRepresentationForTypeIdentifier
                }
                val ext = srcUrl.pathExtension?.takeIf { it.isNotBlank() } ?: "jpg"
                val destPath = "${NSTemporaryDirectory()}photo_${NSUUID().UUIDString()}.$ext"
                val destUrl = NSURL.fileURLWithPath(destPath)

                val ok = NSFileManager.defaultManager.copyItemAtURL(srcUrl, destUrl, error = null)
                if (!ok) {
                    if (cont.isActive) cont.resume(null)
                    return@loadFileRepresentationForTypeIdentifier
                }
                val name = destUrl.lastPathComponent.orEmpty()
                if (cont.isActive) cont.resume(
                    PickedPhoto(uri = destUrl.absoluteString.orEmpty(), name = name)
                )
            }
        }

    private fun topMostViewController(): UIViewController? {
        @Suppress("DEPRECATION")
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }
}

/** PHPickerViewControllerDelegate Kotlin/Native 어댑터. */
private class PhotoPickerDelegate(
    private val onFinish: (PHPickerViewController, List<PHPickerResult>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    @Suppress("UNCHECKED_CAST")
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        onFinish(picker, results)
    }
}
