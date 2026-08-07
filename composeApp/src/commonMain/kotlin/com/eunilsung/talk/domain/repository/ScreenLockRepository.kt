package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.domain.model.ScreenLockConfig

/** 화면 잠금 설정 영속 + 검증. 비밀번호/패턴은 salt + SHA-256 해시로만 저장. */
interface ScreenLockRepository {

    val config: StateFlow<ScreenLockConfig>

    /** 잠금 해제 — 비밀번호/패턴/생체/표시옵션 모두 초기화 (type = NONE). */
    fun disable()

    /** 4자리 PIN 설정(해시 저장) + type = PASSWORD. 생체/표시옵션은 유지. */
    fun setPassword(pin: String)

    /** 패턴 설정(해시 저장) + type = PATTERN. dots 는 방문한 노드 인덱스 순서. */
    fun setPattern(dots: List<Int>)

    /** 현재 저장된 PIN 해시와 일치하는지. type != PASSWORD 면 false. */
    fun verifyPassword(pin: String): Boolean

    /** 현재 저장된 패턴 해시와 일치하는지. type != PATTERN 면 false. */
    fun verifyPattern(dots: List<Int>): Boolean

    /** 생체인증 사용 토글 (비밀번호/패턴이 설정돼 있어야 유효). */
    fun setBiometricEnabled(enabled: Boolean)

    /** 패턴 궤적 표시 토글. */
    fun setPatternVisible(visible: Boolean)

    /** 생체인증만 강제 해제 (비밀번호/패턴은 유지). */
    fun clearBiometric()
}
