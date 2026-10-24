package com.eunilsung.talk.domain.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 기기가 직접 받아 와도 되는 미리보기 주소 — 공개된 https 주소만, 내부망·기기 자신·평문 http 는 받지 않는다. */
class LinkPreviewUrlPolicyTest {

    @Test
    fun 공개된_https_주소는_받는다() {
        listOf(
            "https://www.example.com/page",
            "https://example.com",
            "https://EXAMPLE.com:443/a?b=c#d",
            "https://sub.domain.co.kr/path",
            "https://8.8.8.8/",
            "https://172.32.0.1/",
            "https://100.128.0.1/",
        ).forEach { assertTrue(isPreviewableUrl(it), it) }
    }

    @Test
    fun http_와_다른_scheme_은_받지_않는다() {
        listOf("http://example.com", "ftp://example.com", "file:///etc/hosts", "javascript:alert(1)", "")
            .forEach { assertFalse(isPreviewableUrl(it), it) }
    }

    @Test
    fun 사설_루프백_링크로컬_CGNAT_주소는_받지_않는다() {
        listOf(
            "https://127.0.0.1/",
            "https://10.0.0.5/",
            "https://172.16.0.1/",
            "https://172.31.255.255/",
            "https://192.168.1.1/admin",
            "https://169.254.169.254/latest/meta-data",
            "https://100.64.0.1/",
            "https://0.0.0.0/",
            "https://224.0.0.1/",
            "https://255.255.255.255/",
        ).forEach { assertFalse(isPreviewableUrl(it), it) }
    }

    @Test
    fun 숫자_주소를_줄이거나_바꿔_쓴_것은_받지_않는다() {
        listOf(
            "https://127.1/",
            "https://0x7f.0.0.1/",
            "https://0177.0.0.1/",
            "https://2130706433/",
            "https://%31%32%37.0.0.1/",
            "https://１２７.０.０.１/",
            "https://[::1]/",
            "https://[fe80::1]/",
            "https://[::ffff:127.0.0.1]/",
            "https://example.123/",
        ).forEach { assertFalse(isPreviewableUrl(it), it) }
    }

    @Test
    fun 내부망_이름은_받지_않는다() {
        listOf(
            "https://localhost/",
            "https://localhost:8090/",
            "https://localhost./",
            "https://api.localhost/",
            "https://printer.local/",
            "https://router/",
            "https://intranet.internal/",
            "https://nas.lan/",
            "https://box.home.arpa/",
        ).forEach { assertFalse(isPreviewableUrl(it), it) }
    }

    @Test
    fun 사용자_정보나_역슬래시로_호스트를_속인_주소는_받지_않는다() {
        listOf(
            "https://example.com@127.0.0.1/",
            "https://user:pw@example.com/",
            "https://127.0.0.1\\@example.com/",
            "https://example.com:abc/",
        ).forEach { assertFalse(isPreviewableUrl(it), it) }
    }
}
