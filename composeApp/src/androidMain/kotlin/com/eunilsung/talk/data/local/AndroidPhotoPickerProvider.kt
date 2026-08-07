package com.eunilsung.talk.data.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.eunilsung.talk.util.Log

/** Android [PhotoPickerProvider] — 권한 있으면 갤러리 앱, 없으면 시스템 Photo Picker 로 분기. */
class AndroidPhotoPickerProvider : PhotoPickerProvider {

    override val results: SharedFlow<List<PickedPhoto>> =
        AndroidActivityHolder.pickedPhotos.asSharedFlow()

    override fun launchPhotoPicker(allowMultiple: Boolean, maxItems: Int) {
        if (hasReadImagesPermission()) {
            val launcher = AndroidActivityHolder.galleryContentLauncher
            if (launcher != null) {
                runCatching { launcher.launch("image/*") }
                    .onFailure {
                        Log.message("[Gallery] launch failed: ${it.message} — fallback to Photo Picker")
                        launchSystemPhotoPicker()
                    }
                return
            }
            launchSystemPhotoPicker()
            return
        }

        launchSystemPhotoPicker()
    }

    /** PickMultipleVisualMedia (시스템 Photo Picker) 발화. */
    private fun launchSystemPhotoPicker() {
        val launcher = AndroidActivityHolder.photoPickerLauncher
        if (launcher == null) {
            Log.message("[PhotoPicker] launcher not registered (MainActivity not in foreground?)")
            return
        }
        runCatching {
            launcher.launch(
                PickVisualMediaRequest(
                    mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }.onFailure { Log.message("[PhotoPicker] launch failed: ${it.message}") }
    }

    /** 사진 권한 보유 여부 — Activity 가 없으면 false. */
    private fun hasReadImagesPermission(): Boolean {
        val activity = AndroidActivityHolder.activity ?: return false
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
    }
}
