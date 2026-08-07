package com.eunilsung.talk.domain.model

/** 화면 잠금 방식. */
enum class LockType { NONE, PASSWORD, PATTERN }

/**
 * 화면 잠금 설정 상태.
 *
 * @param biometricEnabled 생체인증 사용 여부. [type] 이 PASSWORD/PATTERN 일 때만 의미.
 * @param patternVisible  패턴 입력 시 궤적(선) 표시 여부. [type] == PATTERN 일 때만 의미.
 */
data class ScreenLockConfig(
    val type: LockType = LockType.NONE,
    val biometricEnabled: Boolean = false,
    val patternVisible: Boolean = true,
) {
    val isEnabled: Boolean get() = type != LockType.NONE
}
