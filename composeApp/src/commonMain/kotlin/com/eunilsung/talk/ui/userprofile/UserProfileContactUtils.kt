package com.eunilsung.talk.ui.userprofile

import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.ui.uikit.contact.ContactDraft

private const val EXTENSION_DIAL_PREFIX = "0704673"

internal fun UserProfile.toContactDraft(): ContactDraft {
    val extension = extensionNum.ifBlank { localCallNum }
    val phones = buildList {
        if (extension.isNotBlank()) add("내선" to "$EXTENSION_DIAL_PREFIX$extension")
        if (mobileNum.isNotBlank()) add("휴대폰" to mobileNum)
    }
    return ContactDraft(
        name = name.ifBlank { userId },
        company = department,
        department = department,
        jobTitle = position,
        phoneNumbers = phones,
        emails = if (email.isNotBlank()) listOf(email) else emptyList(),
    )
}

internal fun handleCallAction(
    profile: UserProfile,
    onPickNumber: (String) -> Unit,
    showPickerDialog: (items: List<String>, onPicked: (String) -> Unit) -> Unit,
    showToast: (String) -> Unit,
) {
    val extension = profile.extensionNum.ifBlank { profile.localCallNum }
    val mobile = profile.mobileNum

    val numbers = buildList {
        if (extension.isNotBlank()) add("$EXTENSION_DIAL_PREFIX$extension")
        if (mobile.isNotBlank()) add(mobile)
    }.distinct()

    if (numbers.isEmpty()) {
        showToast("전화할 번호가 없습니다")
        return
    }

    showPickerDialog(numbers, onPickNumber)
}
