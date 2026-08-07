package com.eunilsung.talk.ui.uikit.contact

import androidx.compose.runtime.Composable

data class ContactDraft(
    val name: String,
    val company: String = "",
    val department: String = "",
    val jobTitle: String = "",
    val phoneNumbers: List<Pair<String, String>> = emptyList(),
    val emails: List<String> = emptyList(),
)

@Composable
expect fun rememberAddContactLauncher(): (ContactDraft) -> Unit
