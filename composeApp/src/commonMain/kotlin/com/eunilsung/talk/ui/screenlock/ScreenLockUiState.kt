package com.eunilsung.talk.ui.screenlock

import com.eunilsung.talk.domain.model.ScreenLockConfig

/** 화면 잠금 설정·잠금 화면이 함께 보는 상태. */
data class ScreenLockUiState(
    val config: ScreenLockConfig = ScreenLockConfig(),
    /** 기기에 생체 정보가 등록돼 있어 지금 쓸 수 있는지. */
    val biometricAvailable: Boolean = false,
    /**
     * 잠금 화면에서 비밀번호·패턴을 틀린 횟수. 풀리면 0 으로 돌아간다.
     *
     * 틀릴 때마다 값이 바뀌므로 입력칸은 이 값이 바뀔 때 흔들고 비운다.
     */
    val unlockFailCount: Int = 0,
    val isAndroid: Boolean = true,
)
