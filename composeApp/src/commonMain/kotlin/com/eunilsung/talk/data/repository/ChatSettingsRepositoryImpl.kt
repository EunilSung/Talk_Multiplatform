package com.eunilsung.talk.data.repository

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.eunilsung.talk.domain.repository.ChatSettingsRepository
import com.eunilsung.talk.domain.repository.ChatSettingsState

class ChatSettingsRepositoryImpl(
    private val settings: Settings,
) : ChatSettingsRepository {

    private val _state = MutableStateFlow(loadFromSettings())
    override val state: StateFlow<ChatSettingsState> = _state.asStateFlow()

    private fun loadFromSettings(): ChatSettingsState {
        val rawSize = settings.getInt(
            SettingsKeys.KEY_CHAT_FONT_SIZE,
            ChatSettingsRepository.DEFAULT_FONT_SIZE
        )
        val safeSize = ChatSettingsRepository.FONT_SIZE_OPTIONS
            .minByOrNull { kotlin.math.abs(it - rawSize) }
            ?: ChatSettingsRepository.DEFAULT_FONT_SIZE
        return ChatSettingsState(
            enterToSend = settings.getBoolean(SettingsKeys.KEY_CHAT_ENTER_SEND, false),
            fontSize = safeSize,
        )
    }

    override fun setEnterToSend(enabled: Boolean) {
        settings.putBoolean(SettingsKeys.KEY_CHAT_ENTER_SEND, enabled)
        _state.value = _state.value.copy(enterToSend = enabled)
    }

    override fun setFontSize(size: Int) {
        val snapped = ChatSettingsRepository.FONT_SIZE_OPTIONS
            .minByOrNull { kotlin.math.abs(it - size) }
            ?: ChatSettingsRepository.DEFAULT_FONT_SIZE
        settings.putInt(SettingsKeys.KEY_CHAT_FONT_SIZE, snapped)
        _state.value = _state.value.copy(fontSize = snapped)
    }

    override fun getLastEmoticonTab(): Int =
        settings.getInt(SettingsKeys.KEY_EMOTICON_LAST_TAB, 0).coerceAtLeast(0)

    override fun setLastEmoticonTab(index: Int) {
        settings.putInt(SettingsKeys.KEY_EMOTICON_LAST_TAB, index.coerceAtLeast(0))
    }
}
