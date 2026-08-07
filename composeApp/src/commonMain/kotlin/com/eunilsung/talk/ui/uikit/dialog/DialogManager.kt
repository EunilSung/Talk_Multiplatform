package com.eunilsung.talk.ui.uikit.dialog

import androidx.compose.runtime.staticCompositionLocalOf

/** 버튼 문구는 null 이면 DialogHost 가 로케일 리소스(확인/취소)로 채운다. */
interface DialogManager {
    fun show(request: DialogRequest)

    fun dismiss()

    fun alert(message: String, onConfirm: () -> Unit = {}) {
        show(DialogRequest.Alert(message = message, onConfirm = onConfirm))
    }

    fun confirm(
        title: String,
        message: String,
        confirmText: String? = null,
        dismissText: String? = null,
        onCancel: () -> Unit = {},
        onConfirm: () -> Unit
    ) {
        show(
            DialogRequest.Confirm(
                title = title,
                message = message,
                confirmText = confirmText,
                dismissText = dismissText,
                onCancel = onCancel,
                onConfirm = onConfirm
            )
        )
    }

    fun list(
        title: String,
        items: List<String>,
        selected: String? = null,
        destructiveItems: Set<String> = emptySet(),
        submenuItems: Set<String> = emptySet(),
        onCancel: () -> Unit = {},
        onSelected: (String) -> Unit
    ) {
        show(
            DialogRequest.ListSelect(
                title = title,
                items = items,
                destructiveItems = destructiveItems,
                submenuItems = submenuItems,
                onCancel = onCancel,
                onSelected = onSelected
            )
        )
    }

    fun textInput(
        title: String,
        initialValue: String = "",
        hint: String = "",
        confirmText: String? = null,
        dismissText: String? = null,
        onCancel: () -> Unit = {},
        onConfirm: (String) -> Unit
    ) {
        show(
            DialogRequest.TextInput(
                title = title,
                initialValue = initialValue,
                hint = hint,
                confirmText = confirmText,
                dismissText = dismissText,
                onCancel = onCancel,
                onConfirm = onConfirm
            )
        )
    }
}

sealed interface DialogRequest {
    data class Alert(
        val message: String,
        val confirmText: String? = null,
        val onConfirm: () -> Unit = {}
    ) : DialogRequest

    data class Confirm(
        val title: String,
        val message: String,
        val confirmText: String? = null,
        val dismissText: String? = null,
        val onCancel: () -> Unit = {},
        val onConfirm: () -> Unit
    ) : DialogRequest

    data class ListSelect(
        val title: String,
        val items: List<String>,
        val destructiveItems: Set<String> = emptySet(),
        val submenuItems: Set<String> = emptySet(),
        val onCancel: () -> Unit = {},
        val onSelected: (String) -> Unit
    ) : DialogRequest

    data class TextInput(
        val title: String,
        val initialValue: String = "",
        val hint: String = "",
        val confirmText: String? = null,
        val dismissText: String? = null,
        val onCancel: () -> Unit = {},
        val onConfirm: (String) -> Unit
    ) : DialogRequest
}

val LocalDialogManager = staticCompositionLocalOf<DialogManager> {
    object : DialogManager {
        override fun show(request: DialogRequest) { }
        override fun dismiss() { }
    }
}
