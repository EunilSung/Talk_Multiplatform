package com.eunilsung.talk

import coil3.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import com.eunilsung.talk.data.local.AppExiter
import com.eunilsung.talk.data.local.AppVersionProvider
import com.eunilsung.talk.data.local.DatabaseDriverFactory
import com.eunilsung.talk.data.local.ExternalUrlOpener
import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.data.local.FileOpener
import com.eunilsung.talk.data.local.FilePickerProvider
import com.eunilsung.talk.data.local.IosAppExiter
import com.eunilsung.talk.data.local.IosAppVersionProvider
import com.eunilsung.talk.data.local.IosExternalUrlOpener
import com.eunilsung.talk.data.local.IosFileMetadataResolver
import com.eunilsung.talk.data.local.IosFileOpener
import com.eunilsung.talk.data.local.IosFilePickerProvider
import com.eunilsung.talk.data.local.IosPhotoPickerProvider
import com.eunilsung.talk.data.local.IosRecentPhotosProvider
import com.eunilsung.talk.data.local.IosVideoThumbnailLoader
import com.eunilsung.talk.data.local.PhotoPickerProvider
import com.eunilsung.talk.data.local.RecentPhotosProvider
import com.eunilsung.talk.data.local.VideoThumbCache
import com.eunilsung.talk.data.local.VideoThumbnailLoader
import com.eunilsung.talk.data.remote.push.IosNotificationSoundPreviewer
import com.eunilsung.talk.data.remote.push.IosPushNotifier
import com.eunilsung.talk.data.remote.push.IosSystemNotificationGate
import com.eunilsung.talk.data.remote.push.NotificationSoundPreviewer
import com.eunilsung.talk.data.remote.push.PushNotifier
import com.eunilsung.talk.domain.repository.SystemNotificationGate
import org.koin.dsl.module
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = "IPHONE"
    override val deviceId: String = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown_ios"
    override val deviceModel: String = UIDevice.currentDevice.model
}

val platformModule = module {
    single<Platform> { IOSPlatform() }
    single { DatabaseDriverFactory() }
    single { PlatformContext.INSTANCE }
    single<RecentPhotosProvider> { IosRecentPhotosProvider() }
    single<FilePickerProvider> { IosFilePickerProvider() }
    single<PhotoPickerProvider> { IosPhotoPickerProvider() }
    single<FileMetadataResolver> { IosFileMetadataResolver() }
    // 스크롤로 말풍선이 드나들 때마다 다시 디코드하지 않도록 캐시로 감싼다.
    single<VideoThumbnailLoader> { VideoThumbCache(IosVideoThumbnailLoader()) }
    single<FileOpener> { IosFileOpener() }
    single<PushNotifier> { IosPushNotifier(get()) }
    single<SystemNotificationGate> { IosSystemNotificationGate() }
    single<NotificationSoundPreviewer> { IosNotificationSoundPreviewer() }
    single { HttpClient(Darwin) }
    single<AppVersionProvider> { IosAppVersionProvider() }
    single<com.eunilsung.talk.data.local.BiometricAuthenticator> {
        com.eunilsung.talk.data.local.IosBiometricAuthenticator()
    }
    single<ExternalUrlOpener> { IosExternalUrlOpener() }
    single<AppExiter> { IosAppExiter() }
}


