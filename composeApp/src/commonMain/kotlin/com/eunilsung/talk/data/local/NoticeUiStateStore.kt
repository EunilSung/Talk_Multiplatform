package com.eunilsung.talk.data.local

import com.russhwolf.settings.Settings

/** 대화방 공지 UI state (숨김/접힘/상세표시) 를 identityKey 별로 영속화. */
class NoticeUiStateStore(
    private val settings: Settings,
) {
    fun isHidden(identityKey: String): Boolean =
        settings.getBoolean(hiddenKey(identityKey), false)

    fun setHidden(identityKey: String, hidden: Boolean) {
        settings.putBoolean(hiddenKey(identityKey), hidden)
    }

    fun isCollapsed(identityKey: String): Boolean =
        settings.getBoolean(collapsedKey(identityKey), false)

    fun setCollapsed(identityKey: String, collapsed: Boolean) {
        settings.putBoolean(collapsedKey(identityKey), collapsed)
    }

    fun isDetailsShown(identityKey: String): Boolean =
        settings.getBoolean(detailsKey(identityKey), true)

    fun setDetailsShown(identityKey: String, shown: Boolean) {
        settings.putBoolean(detailsKey(identityKey), shown)
    }

    private fun hiddenKey(id: String) = "$KEY_PREFIX.hidden.$id"
    private fun collapsedKey(id: String) = "$KEY_PREFIX.collapsed.$id"
    private fun detailsKey(id: String) = "$KEY_PREFIX.details.$id"

    companion object {
        private const val KEY_PREFIX = "notice"
    }
}
