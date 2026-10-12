package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.BiometricResult

/** 생체인증 — Android 지문 / iOS Face·Touch ID. */
interface BiometricAuthenticator {

    /** 기기에 지금 사용 가능한 생체가 등록돼 있는지. */
    fun isAvailable(): Boolean

    /**
     * 생체 프롬프트를 띄우고 결과를 반환. UI 컨텍스트(메인)에서 호출.
     * @param negativeText 취소 버튼 문구 (iOS 는 fallback 버튼 문구로 사용)
     */
    suspend fun authenticate(title: String, subtitle: String, negativeText: String): BiometricResult
}
