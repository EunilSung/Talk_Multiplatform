package com.eunilsung.talk.data.local

/** 다른 앱을 그 앱의 링크(고유 스킴)로 연다 — 플랫폼별 구현. */
interface AppLinkOpener {
    /** [url] 을 받을 앱을 열었으면 true, 받을 앱이 기기에 없으면 false. */
    suspend fun open(url: String): Boolean
}
