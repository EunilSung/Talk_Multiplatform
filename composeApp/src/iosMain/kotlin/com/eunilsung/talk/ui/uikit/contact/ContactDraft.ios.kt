@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.eunilsung.talk.ui.uikit.contact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Contacts.CNContact
import platform.Contacts.CNLabelHome
import platform.Contacts.CNLabelPhoneNumberMain
import platform.Contacts.CNLabelPhoneNumberMobile
import platform.Contacts.CNLabelWork
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNMutableContact
import platform.Contacts.CNPhoneNumber
import platform.ContactsUI.CNContactViewController
import platform.ContactsUI.CNContactViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** iOS — `CNContactViewController(forNewContact:)` 를 NavigationController 로 감싸 modally present. */
@Composable
actual fun rememberAddContactLauncher(): (ContactDraft) -> Unit {
    return remember {
        { draft ->
            val contact = CNMutableContact().apply {
                setGivenName(draft.name)
                if (draft.company.isNotBlank()) setOrganizationName(draft.company)
                if (draft.department.isNotBlank()) setDepartmentName(draft.department)
                if (draft.jobTitle.isNotBlank()) setJobTitle(draft.jobTitle)

                val phones = draft.phoneNumbers
                    .filter { it.second.isNotBlank() }
                    .map { (label, number) ->
                        CNLabeledValue.labeledValueWithLabel(
                            label = labelForPhone(label),
                            value = CNPhoneNumber.phoneNumberWithStringValue(number),
                        )
                    }
                if (phones.isNotEmpty()) setPhoneNumbers(phones)

            }

            val nav = UINavigationController()
            val controller = CNContactViewController.viewControllerForNewContact(contact)

            lateinit var savedDelegate: ContactSaveDelegate
            savedDelegate = ContactSaveDelegate {
                nav.dismissViewControllerAnimated(flag = true) {
                    DelegateRetainer.release(savedDelegate)
                }
            }
            DelegateRetainer.retain(savedDelegate)
            controller.setDelegate(savedDelegate)

            nav.setViewControllers(listOf(controller))

            val top = topPresentedViewController()
            if (top != null) {
                top.presentViewController(nav, animated = true, completion = null)
            } else {
                DelegateRetainer.release(savedDelegate)
            }
        }
    }
}

/** 표시용 라벨 ("내선", "휴대폰" 등) → CN 라벨 상수 매핑. */
private fun labelForPhone(label: String): String = when {
    label.contains("휴대") || label.contains("핸드") || label.contains("모바일") ||
            label.contains("mobile", ignoreCase = true) -> CNLabelPhoneNumberMobile
    label.contains("내선") || label.contains("회사") ||
            label.contains("work", ignoreCase = true) -> CNLabelWork
    label.contains("집") || label.contains("home", ignoreCase = true) -> CNLabelHome
    else -> CNLabelPhoneNumberMain
}

/** present 가능한 가장 위의 view controller — alert / 시트 위에도 띄울 수 있게. */
private fun topPresentedViewController(): UIViewController? {
    var current: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (current?.presentedViewController != null) {
        current = current.presentedViewController
    }
    return current
}

/** `CNContactViewController` 완료/취소 시 [onComplete] 발화. */
private class ContactSaveDelegate(
    private val onComplete: () -> Unit,
) : NSObject(), CNContactViewControllerDelegateProtocol {
    override fun contactViewController(
        viewController: CNContactViewController,
        didCompleteWithContact: CNContact?,
    ) {
        onComplete()
    }
}

/** Delegate 가 GC 되지 않게 강한 참조로 잡아두는 컨테이너 (delegate 가 weak 참조라 필요). */
private object DelegateRetainer {
    private val retained = mutableSetOf<ContactSaveDelegate>()
    fun retain(delegate: ContactSaveDelegate) { retained.add(delegate) }
    fun release(delegate: ContactSaveDelegate) { retained.remove(delegate) }
}
