package com.eunilsung.talk.ui.main

import com.eunilsung.talk.Config
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.MainEvent
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.repository.MainRepository
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.GroupUseCases
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.domain.repository.AppMaintenanceRepository

class MainViewModel(
    mainRepository: MainRepository,
    loginRepository: LoginRepository,
    private val groupUseCases: GroupUseCases,
    private val chatRoomListUseCases: ChatRoomListUseCases,
    private val maintenance: AppMaintenanceRepository,
) : ViewModel() {

    val events: Flow<MainEvent> = mainRepository.events

    /**
     * 이미지 캐시를 오늘 이미 비웠는지.
     *
     * [Config.UserProfile.IS_KEEP_CACHE_DAY] 가 false 면 하루 1회 제한을 두지 않고
     * 앱 실행마다 비운다.
     */
    fun shouldClearImageCache(today: String): Boolean {
        if (!Config.UserProfile.IS_KEEP_CACHE_DAY) return true
        return maintenance.imageCacheClearedDate() != today
    }

    fun markImageCacheCleared(today: String) {
        maintenance.setImageCacheClearedDate(today)
    }

    /**
     * 이번 버전의 안내 팝업을 아직 안 봤으면 버전 문자열, 이미 봤거나 버전을 모르면 null.
     *
     * 안내는 "서버 없이 기기 안 데이터로 도는 샘플"이라는 내용이라 서버 모드에서는 띄우지 않는다.
     */
    fun pendingNoticeVersion(): String? {
        if (Config.Server.IS_ENABLED) return null
        val version = maintenance.currentVersionName().takeIf { it.isNotBlank() } ?: return null
        val shown = maintenance.noticeShownVersion()
        return version.takeIf { it != shown }
    }

    fun markNoticeShown(version: String) {
        maintenance.setNoticeShownVersion(version)
    }

    init {
        viewModelScope.launch {
            loginRepository.isLoggedIn
                .filter { it }
                .collect {
                    Log.message("Login success — prefetching tab data")
                    launch { runCatching { groupUseCases.fetchGroups() } }
                    launch { runCatching { chatRoomListUseCases.fetchChatRooms() } }
                }
        }
    }
}
