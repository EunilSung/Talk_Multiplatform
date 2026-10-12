package com.eunilsung.talk.ui.main

import com.eunilsung.talk.domain.model.PushNavRequest
import com.eunilsung.talk.domain.model.SharedContent

sealed interface MainActions {
    /** 앱이 화면 앞으로 나왔다. 끊겨 있던 연결을 다시 확인한다. */
    data object OnForeground : MainActions

    data object OnBackground : MainActions

    /** [MainScreenEvent.ClearImageCache] 를 받아 캐시를 비웠다. */
    data class OnImageCacheCleared(val date: String) : MainActions

    /** 버전 안내를 확인했다. 같은 버전에서는 다시 띄우지 않는다. */
    data class OnNoticeConfirmed(val version: String) : MainActions

    data object Logout : MainActions
}

/** 메인 화면이 한 번씩 처리할 일. 다이얼로그·이동처럼 상태로 들고 있을 수 없는 것들이다. */
sealed interface MainScreenEvent {
    /** 오늘 아직 이미지 캐시를 비우지 않았다. 비운 뒤 [MainActions.OnImageCacheCleared] 로 알린다. */
    data class ClearImageCache(val date: String) : MainScreenEvent

    data class ShowNotice(val version: String) : MainScreenEvent

    /** 서버가 이 기기의 로그인을 끊었다. 안내를 확인하면 로그아웃한다. */
    data class ForcedLogout(val message: String) : MainScreenEvent

    /** 로그아웃했다. 열려 있던 화면을 닫는다. */
    data object LoggedOut : MainScreenEvent

    /** 푸시 알림을 눌러 들어왔다. 떠 있는 다이얼로그·시트를 걷고 그 화면을 연다. */
    data class OpenPush(val request: PushNavRequest) : MainScreenEvent

    /** 다른 앱에서 공유해 들어왔다. */
    data class OpenShare(val content: SharedContent) : MainScreenEvent
}
