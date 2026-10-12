package com.eunilsung.talk.domain.model

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
