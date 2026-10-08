package com.eunilsung.talk.ui.userprofile

import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.ui.uikit.contact.ContactDraft

private const val EXTENSION_DIAL_PREFIX = "0704673"

internal fun UserProfile.toContactDraft(extensionLabel: String, mobileLabel: String): ContactDraft {
    val extension = extensionNum.ifBlank { localCallNum }
    val phones = buildList {
        if (extension.isNotBlank()) add(extensionLabel to "$EXTENSION_DIAL_PREFIX$extension")
        if (mobileNum.isNotBlank()) add(mobileLabel to mobileNum)
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
    noNumberMessage: String,
    showToast: (String) -> Unit,
) {
    val extension = profile.extensionNum.ifBlank { profile.localCallNum }
    val mobile = profile.mobileNum

    val numbers = buildList {
        if (extension.isNotBlank()) add("$EXTENSION_DIAL_PREFIX$extension")
        if (mobile.isNotBlank()) add(mobile)
    }.distinct()

    if (numbers.isEmpty()) {
        showToast(noNumberMessage)
        return
    }

    showPickerDialog(numbers, onPickNumber)
}
