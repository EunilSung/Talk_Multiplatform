package com.eunilsung.talk.data.local

/** 현재 설치된 앱의 버전 정보 제공. */
interface AppVersionProvider {
    /** 빌드 버전 번호 (정수). */
    fun currentVersionCode(): Long

    /** 사용자에게 보여줄 마케팅 버전명. 실패/미설정 시 빈 문자열. */
    fun currentVersionName(): String
}
