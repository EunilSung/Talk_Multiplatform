package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.local.AppVersionProvider
import com.eunilsung.talk.domain.repository.AppMaintenanceRepository
import com.russhwolf.settings.Settings

/** 앱을 켤 때마다 확인하는 기록을 기기 설정에 둔다. */
class AppMaintenanceRepositoryImpl(
    private val settings: Settings,
    private val appVersionProvider: AppVersionProvider,
) : AppMaintenanceRepository {

    override fun imageCacheClearedDate(): String? = settings.getStringOrNull(SettingsKeys.KEY_IMAGE_CACHE_CLEAR_DATE)

    override fun setImageCacheClearedDate(date: String) {
        settings.putString(SettingsKeys.KEY_IMAGE_CACHE_CLEAR_DATE, date)
    }

    override fun currentVersionName(): String = appVersionProvider.currentVersionName()

    override fun noticeShownVersion(): String? = settings.getStringOrNull(SettingsKeys.KEY_NOTICE_SHOWN_VERSION)

    override fun setNoticeShownVersion(version: String) {
        settings.putString(SettingsKeys.KEY_NOTICE_SHOWN_VERSION, version)
    }
}
