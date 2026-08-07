package com.eunilsung.talk.data.local

/** 생체인증 결과. */
enum class BiometricResult {
    /** 인증 성공 */
    SUCCESS,
    /** 인증 실패 (불일치 등) — 재시도 가능 */
    FAILED,
    /** 사용자가 취소 (취소 버튼 / 시스템 취소) */
    CANCELED,
    /** 생체 사용 불가 — 기기 미지원 또는 등록된 생체 없음/삭제됨 */
    UNAVAILABLE,
}

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
