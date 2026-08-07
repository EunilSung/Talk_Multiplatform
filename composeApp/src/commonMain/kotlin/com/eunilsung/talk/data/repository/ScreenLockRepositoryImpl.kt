package com.eunilsung.talk.data.repository

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.eunilsung.talk.domain.model.LockType
import com.eunilsung.talk.domain.model.ScreenLockConfig
import com.eunilsung.talk.domain.repository.ScreenLockRepository
import com.eunilsung.talk.util.LockHash

/** [ScreenLockRepository] 구현 — [Settings] 에 PIN/패턴을 [LockHash] 해시로 저장. */
class ScreenLockRepositoryImpl(
    private val settings: Settings,
) : ScreenLockRepository {

    private val _config = MutableStateFlow(loadFromSettings())
    override val config: StateFlow<ScreenLockConfig> = _config.asStateFlow()

    private fun loadFromSettings(): ScreenLockConfig {
        val type = when (settings.getString(SettingsKeys.KEY_LOCK_TYPE, LockType.NONE.name)) {
            LockType.PASSWORD.name -> LockType.PASSWORD
            LockType.PATTERN.name -> LockType.PATTERN
            else -> LockType.NONE
        }
        val hasSecret = settings.getString(SettingsKeys.KEY_LOCK_HASH, "").isNotBlank()
        return ScreenLockConfig(
            type = if (hasSecret) type else LockType.NONE,
            biometricEnabled = settings.getBoolean(SettingsKeys.KEY_LOCK_BIOMETRIC, false),
            patternVisible = settings.getBoolean(SettingsKeys.KEY_LOCK_PATTERN_VISIBLE, true),
        )
    }

    override fun disable() {
        settings.remove(SettingsKeys.KEY_LOCK_HASH)
        settings.remove(SettingsKeys.KEY_LOCK_SALT)
        settings.putString(SettingsKeys.KEY_LOCK_TYPE, LockType.NONE.name)
        settings.putBoolean(SettingsKeys.KEY_LOCK_BIOMETRIC, false)
        _config.value = ScreenLockConfig(
            type = LockType.NONE,
            biometricEnabled = false,
            patternVisible = _config.value.patternVisible,
        )
    }

    override fun setPassword(pin: String) {
        storeSecret(LockType.PASSWORD, pin)
    }

    override fun setPattern(dots: List<Int>) {
        storeSecret(LockType.PATTERN, dots.joinToString("-"))
    }

    private fun storeSecret(type: LockType, secret: String) {
        val salt = LockHash.newSalt()
        settings.putString(SettingsKeys.KEY_LOCK_SALT, salt)
        settings.putString(SettingsKeys.KEY_LOCK_HASH, LockHash.hash(secret, salt))
        settings.putString(SettingsKeys.KEY_LOCK_TYPE, type.name)
        _config.value = _config.value.copy(type = type)
    }

    override fun verifyPassword(pin: String): Boolean =
        _config.value.type == LockType.PASSWORD && matches(pin)

    override fun verifyPattern(dots: List<Int>): Boolean =
        _config.value.type == LockType.PATTERN && matches(dots.joinToString("-"))

    private fun matches(secret: String): Boolean {
        val salt = settings.getString(SettingsKeys.KEY_LOCK_SALT, "")
        val stored = settings.getString(SettingsKeys.KEY_LOCK_HASH, "")
        if (salt.isBlank() || stored.isBlank()) return false
        return LockHash.hash(secret, salt) == stored
    }

    override fun setBiometricEnabled(enabled: Boolean) {
        settings.putBoolean(SettingsKeys.KEY_LOCK_BIOMETRIC, enabled)
        _config.value = _config.value.copy(biometricEnabled = enabled)
    }

    override fun setPatternVisible(visible: Boolean) {
        settings.putBoolean(SettingsKeys.KEY_LOCK_PATTERN_VISIBLE, visible)
        _config.value = _config.value.copy(patternVisible = visible)
    }

    override fun clearBiometric() {
        settings.putBoolean(SettingsKeys.KEY_LOCK_BIOMETRIC, false)
        _config.value = _config.value.copy(biometricEnabled = false)
    }
}
