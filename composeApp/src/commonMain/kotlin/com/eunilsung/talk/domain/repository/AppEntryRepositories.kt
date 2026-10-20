package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.PushNavRequest
import com.eunilsung.talk.domain.model.SharedContent
import kotlinx.coroutines.flow.StateFlow

/** 푸시 알림을 눌러 들어왔을 때 열어야 할 화면. 플랫폼이 넣어 두고 화면이 꺼내 간다. */
interface PendingPushNavigationRepository {
    val pending: StateFlow<PushNavRequest?>

    /** 꺼내 가면서 비운다. 꺼낸 쪽이 바로 그 화면으로 가야 한다. */
    fun consume(): PushNavRequest?
}

/** 다른 앱의 공유로 들어왔을 때 넘겨받은 내용. 플랫폼이 넣어 두고 화면이 꺼내 간다. */
interface PendingShareNavigationRepository {
    val pending: StateFlow<SharedContent?>

    /** 꺼내 가면서 비운다. */
    fun consume(): SharedContent?
}

/** 지금 화면에 띄워 둔 대화방. 그 방의 새 대화는 알림을 띄우지 않고 읽은 것으로 친다. */
interface CurrentChatRoomRepository {
    val currentChatRoomId: String?

    fun set(chatRoomId: String)

    fun clear()
}

/** 공지바를 어떻게 보이게 해 두었는지(숨김·접힘·상세). 공지마다([identityKey]) 따로 기억한다. */
interface NoticeUiStateRepository {
    fun isHidden(identityKey: String): Boolean

    fun setHidden(identityKey: String, hidden: Boolean)

    fun isCollapsed(identityKey: String): Boolean

    fun setCollapsed(identityKey: String, collapsed: Boolean)

    fun isDetailsShown(identityKey: String): Boolean

    fun setDetailsShown(identityKey: String, shown: Boolean)
}

/** 앱을 켤 때마다 확인하는 기록 — 이미지 캐시를 언제 비웠는지. */
interface AppMaintenanceRepository {
    /** 이미지 캐시를 마지막으로 비운 날. 비운 적이 없으면 null. */
    fun imageCacheClearedDate(): String?

    fun setImageCacheClearedDate(date: String)
}
