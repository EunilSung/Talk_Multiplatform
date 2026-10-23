package com.eunilsung.talk.testsupport

import com.eunilsung.talk.data.local.AppLinkOpener

/** 다른 앱 열기의 대역. 열어 달라고 한 링크를 모아 두고, [isInstalled] 로 받을 앱이 있는지를 정한다. */
class FakeAppLinkOpener(var isInstalled: Boolean = true) : AppLinkOpener {
    val requested = mutableListOf<String>()

    override suspend fun open(url: String): Boolean {
        requested += url
        return isInstalled
    }
}
