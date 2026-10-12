package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.PushPayload

/** 푸시 알림 표시 / 취소 / 배지 추상. 플랫폼별 `platformModule` 에서 바인딩. */
interface PushNotifier {

    /** 알림 표시. tag = [PushPayload.msgKey] 로 식별. */
    fun show(payload: PushPayload)

    /** 특정 키의 알림 제거. */
    fun cancel(key: String)

    /** 런처 배지 카운트 설정. iOS 만 native 지원. */
    fun setBadge(count: Int)
}
