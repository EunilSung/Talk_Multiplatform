package com.eunilsung.talk.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 외부 주소 열기의 규칙 — 대화 링크는 브라우저로만 넘기고, 받아서 설치하는 주소는 강제 업데이트의 https 패키지 주소뿐이다.
 * 강제 업데이트는 그 플랫폼의 스토어 주소도 스토어 앱으로 넘긴다.
 */
class ExternalUrlRulesTest {

    @Test
    fun 브라우저로는_http_와_https_만_넘긴다() {
        assertTrue(isWebUrl("https://example.com/a.apk"))
        assertTrue(isWebUrl(" HTTP://example.com "))
        listOf("intent://scan/#Intent;scheme=zxing;end", "file:///sdcard/a.apk", "market://details?id=x", "itms-services://?action=x", "")
            .forEach { assertFalse(isWebUrl(it), it) }
    }

    @Test
    fun 설치_주소는_https_이고_경로가_패키지_확장자로_끝나야_한다() {
        assertTrue(isInstallableUpdateUrl("https://update.example.com/talk.apk", ".apk"))
        assertTrue(isInstallableUpdateUrl("https://update.example.com/talk.APK?v=10", ".apk"))
        assertTrue(isInstallableUpdateUrl("https://update.example.com/manifest.plist", ".plist"))
    }

    @Test
    fun 평문이거나_조각_뒤에_확장자를_붙인_주소는_설치하지_않는다() {
        assertFalse(isInstallableUpdateUrl("http://update.example.com/talk.apk", ".apk"))
        assertFalse(isInstallableUpdateUrl("https://evil.example.com/page.html#.apk", ".apk"))
        assertFalse(isInstallableUpdateUrl("https://evil.example.com/page?f=.apk", ".apk"))
        assertFalse(isInstallableUpdateUrl("https://update.example.com/talk.apk", ".plist"))
    }

    @Test
    fun 강제_업데이트는_설치_주소를_받아_설치하고_웹_주소는_넘긴다() {
        assertEquals(UpdateUrlTarget.INSTALL, updateUrlTarget("https://update.example.com/talk.apk", UpdateRule.ANDROID))
        assertEquals(UpdateUrlTarget.INSTALL, updateUrlTarget(" https://update.example.com/manifest.plist ", UpdateRule.IOS))
        assertEquals(UpdateUrlTarget.VIEW, updateUrlTarget("https://update.example.com/download", UpdateRule.ANDROID))
        assertEquals(UpdateUrlTarget.VIEW, updateUrlTarget("http://update.example.com/talk.apk", UpdateRule.ANDROID))
        assertEquals(UpdateUrlTarget.VIEW, updateUrlTarget("https://update.example.com/talk.apk", UpdateRule.IOS))
    }

    @Test
    fun 강제_업데이트는_그_플랫폼의_스토어_주소를_스토어_앱으로_넘긴다() {
        assertEquals(UpdateUrlTarget.VIEW, updateUrlTarget("market://details?id=com.example.talk", UpdateRule.ANDROID))
        assertEquals(UpdateUrlTarget.VIEW, updateUrlTarget(" MARKET://details?id=com.example.talk", UpdateRule.ANDROID))
        assertEquals(UpdateUrlTarget.VIEW, updateUrlTarget("itms-apps://apps.apple.com/app/id0000000000", UpdateRule.IOS))
    }

    @Test
    fun 강제_업데이트라도_다른_scheme_과_다른_플랫폼의_스토어_주소는_열지_않는다() {
        listOf(
            "intent://scan/#Intent;scheme=zxing;end",
            "file:///sdcard/talk.apk",
            "market://search?q=talk",
            "itms-apps://apps.apple.com/app/id0000000000",
            "",
        ).forEach { assertEquals(UpdateUrlTarget.NONE, updateUrlTarget(it, UpdateRule.ANDROID), it) }
        listOf(
            "itms-services://?action=download-manifest&url=https://evil.example.com/m.plist",
            "market://details?id=com.example.talk",
            "",
        ).forEach { assertEquals(UpdateUrlTarget.NONE, updateUrlTarget(it, UpdateRule.IOS), it) }
    }
}
