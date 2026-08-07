package com.eunilsung.talk.data.local

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.eunilsung.talk.util.Log

/** Android [FilePickerProvider] — Storage Access Framework 의 `OpenMultipleDocuments` 위임. */
class AndroidFilePickerProvider : FilePickerProvider {

    override val results: SharedFlow<List<PickedFile>> =
        AndroidActivityHolder.pickedFiles.asSharedFlow()

    override var lastRequestId: String = ""
        private set

    override fun launchFilePicker(requestId: String, allowMultiple: Boolean, mimeTypes: List<String>) {
        lastRequestId = requestId
        val launcher = AndroidActivityHolder.filePickerLauncher
        if (launcher == null) {
            Log.message("[FilePicker] launcher not registered (MainActivity not in foreground?)")
            return
        }
        runCatching { launcher.launch(mimeTypes.toTypedArray()) }
            .onFailure { Log.message("[FilePicker] launch failed: ${it.message}") }
    }
}
