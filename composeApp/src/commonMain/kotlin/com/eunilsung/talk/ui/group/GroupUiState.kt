package com.eunilsung.talk.ui.group

import com.eunilsung.talk.domain.model.Group

sealed interface GroupUiState {
    val items: List<Group.Item>
    val isEditMode: Boolean

    data class Idle(
        override val items: List<Group.Item> = emptyList(),
        override val isEditMode: Boolean = false
    ) : GroupUiState

    data class Search(
        override val items: List<Group.Item> = emptyList(),
        override val isEditMode: Boolean = false
    ) : GroupUiState
}
