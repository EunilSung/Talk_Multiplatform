package com.eunilsung.talk.ui.screenlock

sealed interface ScreenLockActions {
    /** 생체 정보가 아직 등록돼 있는지 다시 본다. 없어졌는데 켜져 있으면 끈다. */
    data object ReconcileBiometric : ScreenLockActions

    data object DisableLock : ScreenLockActions

    data class SetPassword(val pin: String) : ScreenLockActions

    data class SetPattern(val dots: List<Int>) : ScreenLockActions

    data class SetPatternVisible(val visible: Boolean) : ScreenLockActions

    /** 켤 때는 기기에서 실제로 쓸 수 있을 때만 켠다. */
    data class SetBiometricEnabled(val enabled: Boolean) : ScreenLockActions

    data class UnlockWithPassword(val pin: String) : ScreenLockActions

    data class UnlockWithPattern(val dots: List<Int>) : ScreenLockActions

    /** 생체 인증 창을 띄운다. 창의 문구는 기기 언어에 맞춰 화면이 넘긴다. */
    data class UnlockWithBiometric(
        val title: String,
        val subtitle: String,
        val negativeText: String,
    ) : ScreenLockActions
}
