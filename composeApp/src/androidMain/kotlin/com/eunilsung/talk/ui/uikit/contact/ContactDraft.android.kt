package com.eunilsung.talk.ui.uikit.contact

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android — `Intent.ACTION_INSERT` 로 기본 연락처 앱의 새 연락처 화면 진입. */
@Composable
actual fun rememberAddContactLauncher(): (ContactDraft) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { draft ->
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                if (draft.name.isNotBlank()) {
                    putExtra(ContactsContract.Intents.Insert.NAME, draft.name)
                }
                if (draft.company.isNotBlank()) {
                    putExtra(ContactsContract.Intents.Insert.COMPANY, draft.company)
                }
                if (draft.jobTitle.isNotBlank()) {
                    putExtra(ContactsContract.Intents.Insert.JOB_TITLE, draft.jobTitle)
                }
                draft.emails.firstOrNull { it.isNotBlank() }?.let {
                    putExtra(ContactsContract.Intents.Insert.EMAIL, it)
                    putExtra(
                        ContactsContract.Intents.Insert.EMAIL_TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_WORK,
                    )
                }

                val phoneSlots = listOf(
                    Triple(
                        ContactsContract.Intents.Insert.PHONE,
                        ContactsContract.Intents.Insert.PHONE_TYPE,
                        ContactsContract.Intents.Insert.PHONE_ISPRIMARY,
                    ),
                    Triple(
                        ContactsContract.Intents.Insert.SECONDARY_PHONE,
                        ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
                        null,
                    ),
                    Triple(
                        ContactsContract.Intents.Insert.TERTIARY_PHONE,
                        ContactsContract.Intents.Insert.TERTIARY_PHONE_TYPE,
                        null,
                    ),
                )
                draft.phoneNumbers
                    .filter { it.second.isNotBlank() }
                    .take(phoneSlots.size)
                    .forEachIndexed { index, (label, number) ->
                        val (numKey, typeKey, primaryKey) = phoneSlots[index]
                        putExtra(numKey, number)
                        putExtra(typeKey, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
                        if (index == 0 && primaryKey != null) putExtra(primaryKey, true)
                    }
            }
            context.startActivity(intent)
        }
    }
}
