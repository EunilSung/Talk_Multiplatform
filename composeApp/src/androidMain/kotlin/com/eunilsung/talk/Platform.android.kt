package com.eunilsung.talk

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.eunilsung.talk.data.local.AndroidAppExiter
import com.eunilsung.talk.data.local.AndroidVideoThumbnailLoader
import com.eunilsung.talk.data.local.VideoThumbCache
import com.eunilsung.talk.data.local.VideoThumbnailLoader
import com.eunilsung.talk.data.local.AndroidAppVersionProvider
import com.eunilsung.talk.data.local.AndroidExternalUrlOpener
import com.eunilsung.talk.data.local.AndroidFileMetadataResolver
import com.eunilsung.talk.data.local.AndroidFileOpener
import com.eunilsung.talk.data.local.AndroidFilePickerProvider
import com.eunilsung.talk.data.local.AndroidPhotoPickerProvider
import com.eunilsung.talk.data.local.AndroidRecentPhotosProvider
import com.eunilsung.talk.data.local.AppExiter
import com.eunilsung.talk.data.local.AppVersionProvider
import com.eunilsung.talk.data.local.DatabaseDriverFactory
import com.eunilsung.talk.data.local.ExternalUrlOpener
import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.data.local.FileOpener
import com.eunilsung.talk.data.local.FilePickerProvider
import com.eunilsung.talk.data.local.PhotoPickerProvider
import com.eunilsung.talk.data.local.RecentPhotosProvider
import com.eunilsung.talk.data.remote.push.AndroidNotificationSoundPreviewer
import com.eunilsung.talk.data.remote.push.AndroidPushNotifier
import com.eunilsung.talk.data.remote.push.AndroidSystemNotificationGate
import com.eunilsung.talk.data.remote.push.NotificationSoundPreviewer
import com.eunilsung.talk.data.remote.push.PushNotifier
import com.eunilsung.talk.domain.repository.SystemNotificationGate
import org.koin.dsl.module

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "ANDROID"
    override val deviceId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    override val deviceModel: String = Build.MODEL
}

val platformModule = module {
    single<Platform> { AndroidPlatform(get()) }
    single { DatabaseDriverFactory(get()) }
    single<RecentPhotosProvider> { AndroidRecentPhotosProvider(get()) }
    single<FilePickerProvider> { AndroidFilePickerProvider() }
    single<PhotoPickerProvider> { AndroidPhotoPickerProvider() }
    single<FileMetadataResolver> { AndroidFileMetadataResolver(get()) }
    // 스크롤로 말풍선이 드나들 때마다 다시 디코드하지 않도록 캐시로 감싼다.
    single<VideoThumbnailLoader> { VideoThumbCache(AndroidVideoThumbnailLoader(get())) }
    single<FileOpener> { AndroidFileOpener(get()) }
    single<PushNotifier> { AndroidPushNotifier(get(), get()) }
    single<SystemNotificationGate> { AndroidSystemNotificationGate(get()) }
    single<NotificationSoundPreviewer> { AndroidNotificationSoundPreviewer(get()) }
    single { HttpClient(OkHttp) }
    single<AppVersionProvider> { AndroidAppVersionProvider(get()) }
    single<com.eunilsung.talk.data.local.BiometricAuthenticator> {
        com.eunilsung.talk.data.local.AndroidBiometricAuthenticator(get())
    }
    single<AppExiter> { AndroidAppExiter() }
    single<ExternalUrlOpener> { AndroidExternalUrlOpener(get(), get()) }
}
