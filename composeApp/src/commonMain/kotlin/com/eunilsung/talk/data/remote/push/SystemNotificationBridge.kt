package com.eunilsung.talk.data.remote.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.repository.NotificationSettingsRepository
import com.eunilsung.talk.util.Log
import org.koin.mp.KoinPlatform

/** 네이티브 진입점 — 앱이 포그라운드로 돌아올 때 시스템 알림 권한 상태를 메신저에 미러. */
object SystemNotificationBridge {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** 시스템 권한 상태를 재조회해 [NotificationSettingsRepository.syncEnabledFromSystem] 호출. */
    fun refreshEnabled() {
        val repository = runCatching {
            KoinPlatform.getKoin().get<NotificationSettingsRepository>()
        }.getOrNull() ?: return

        scope.launch {
            runCatching { repository.syncEnabledFromSystem() }
                .onFailure { Log.message("[SystemNotificationBridge] refreshEnabled failed: ${it.message}") }
        }
    }
}
