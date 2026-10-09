package com.eunilsung.talk

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.launch
import com.eunilsung.talk.data.local.AndroidActivityHolder
import com.eunilsung.talk.data.local.PickedFile
import com.eunilsung.talk.data.local.PickedPhoto
import com.eunilsung.talk.ui.main.MainScreen
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.util.Log

class MainActivity : FragmentActivity() {

    /** 푸시 알림 탭 진입 시 intent extras 의 라우팅 정보를 [PendingPushNavigation] 에 set. */
    private fun handlePushIntent(intent: android.content.Intent?) {
        intent ?: return
        val kind = intent.getStringExtra(
            com.eunilsung.talk.data.remote.push.AndroidPushNotifier.EXTRA_MSG_KIND
        ).orEmpty()
        val key = intent.getStringExtra(
            com.eunilsung.talk.data.remote.push.AndroidPushNotifier.EXTRA_MSG_KEY
        ).orEmpty()
        val cat = intent.getStringExtra(
            com.eunilsung.talk.data.remote.push.AndroidPushNotifier.EXTRA_MSG_CATEGORY
        ).orEmpty()
        if (kind.isNotBlank() && key.isNotBlank()) {
            Log.message("[Push/Android] tap → kind=$kind key=$key cat=$cat")
            com.eunilsung.talk.data.remote.push.PendingPushNavigation.set(kind, key, cat)
            intent.removeExtra(
                com.eunilsung.talk.data.remote.push.AndroidPushNotifier.EXTRA_MSG_KIND
            )
            intent.removeExtra(
                com.eunilsung.talk.data.remote.push.AndroidPushNotifier.EXTRA_MSG_KEY
            )
            intent.removeExtra(
                com.eunilsung.talk.data.remote.push.AndroidPushNotifier.EXTRA_MSG_CATEGORY
            )
        }
    }

    private fun handleShareIntent(intent: android.content.Intent?) {
        intent ?: return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        @Suppress("DEPRECATION")
        val uris: List<Uri> = when (action) {
            Intent.ACTION_SEND -> {
                val single: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(single)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list.orEmpty().toList()
            }
            else -> emptyList()
        }

        val content = com.eunilsung.talk.data.remote.share.SharedContent(
            text = text,
            filePaths = uris.map { it.toString() }
        )
        if (content.isEmpty) return

        Log.message(
            "[Share/Android] received action=$action text=${text.length} files=${uris.size}"
        )
        com.eunilsung.talk.data.remote.share.PendingShareNavigation.set(content)

        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(Intent.EXTRA_STREAM)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
        handleShareIntent(intent)
    }

    /** 포그라운드 복귀 시 시스템 알림 권한 상태를 미러. */
    override fun onResume() {
        super.onResume()
        AppSwitcherSnapshotGuard.onResume(this)
        com.eunilsung.talk.data.remote.push.SystemNotificationBridge.refreshEnabled()
    }

    override fun onPause() {
        super.onPause()
        AppSwitcherSnapshotGuard.onPause(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppSwitcherSnapshotGuard.onConfigurationChanged(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppSwitcherSnapshotGuard.onCreate(this)

        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        requestedOrientation = if (isTablet) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        handlePushIntent(intent)
        handleShareIntent(intent)

        val filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            val picked = uris.map { uri ->
                val (name, size) = queryDisplayNameAndSize(uri) ?: ("file" to -1L)
                PickedFile(uri = uri.toString(), name = name, sizeBytes = size)
            }
            Log.message("[FilePicker] picked ${picked.size} files")
            lifecycleScope.launch { AndroidActivityHolder.pickedFiles.emit(picked) }
        }
        AndroidActivityHolder.activity = this
        AndroidActivityHolder.filePickerLauncher = filePickerLauncher

        val photoPickerLauncher = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
        ) { uris ->
            val picked = uris.map { uri ->
                val name = queryDisplayNameAndSize(uri)?.first.orEmpty()
                PickedPhoto(uri = uri.toString(), name = name)
            }
            Log.message("[PhotoPicker] picked ${picked.size} photos")
            lifecycleScope.launch { AndroidActivityHolder.pickedPhotos.emit(picked) }
        }
        AndroidActivityHolder.photoPickerLauncher = photoPickerLauncher

        val photoPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            if (results.values.any { it }) {
                Log.message("[PhotoPermission] granted=$results")
                return@registerForActivityResult
            }
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
            if (!canAskAgain) {
                Log.message("[PhotoPermission] permanently denied → open settings")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { startActivity(intent) }
            } else {
                Log.message("[PhotoPermission] denied (can ask again)")
            }
        }
        AndroidActivityHolder.photoPermissionLauncher = photoPermissionLauncher

        val galleryContentLauncher = registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            val picked = uris.map { uri ->
                val name = queryDisplayNameAndSize(uri)?.first.orEmpty()
                PickedPhoto(uri = uri.toString(), name = name)
            }
            Log.message("[Gallery] ACTION_GET_CONTENT picked ${picked.size} photos")
            lifecycleScope.launch { AndroidActivityHolder.pickedPhotos.emit(picked) }
        }
        AndroidActivityHolder.galleryContentLauncher = galleryContentLauncher

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                val notifPermLauncher = registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    Log.message("[NotifPermission] granted=$granted")
                }
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AppColors.Light.Bg.toArgb(),
                AppColors.Dark.Bg.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AppColors.Light.Bg.toArgb(),
                AppColors.Dark.Bg.toArgb()
            )
        )

        setContent {
            Navigator(MainScreen)
        }
    }

    override fun onDestroy() {
        if (AndroidActivityHolder.activity === this) {
            AndroidActivityHolder.activity = null
            AndroidActivityHolder.filePickerLauncher = null
            AndroidActivityHolder.photoPickerLauncher = null
            AndroidActivityHolder.photoPermissionLauncher = null
            AndroidActivityHolder.galleryContentLauncher = null
        }
        super.onDestroy()
    }

    /** content URI → (DISPLAY_NAME, SIZE). 못 찾으면 null. */
    private fun queryDisplayNameAndSize(uri: android.net.Uri): Pair<String, Long>? =
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else -1L
                name to size
            }
        }.getOrNull()
}