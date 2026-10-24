package com.eunilsung.talk.data.local

import kotlinx.coroutines.flow.StateFlow

/**
 * 외부 URL 을 기기 밖으로 넘긴다.
 *
 * 대화 링크·링크 미리보기처럼 누구나 보낼 수 있는 주소는 [open] 으로 브라우저에만 넘긴다. 앱을 받아 설치하는 처리는
 * 강제 업데이트 안내([openUpdate])에서만 한다. [isDownloading] 은 업데이트 APK 를 받는 중인지다.
 */
interface ExternalUrlOpener {
    /** [url] 을 기본 브라우저로 연다. http·https 가 아니면 열지 않는다. 내려받기·설치는 하지 않는다. */
    fun open(url: String)

    /**
     * 강제 업데이트 안내의 [url] 을 연다. 어떻게 열지는 [updateUrlTarget] 이 정한다.
     *
     * https 이고 경로가 Android `.apk`·iOS `.plist` 로 끝나면 받아서 설치 화면을 띄운다. 스토어 주소(Android
     * `market://details?id=`, iOS `itms-apps://`)는 스토어 앱으로, 그 밖의 http·https 는 브라우저로 넘긴다.
     * 다른 scheme 은 열지 않는다.
     */
    fun openUpdate(url: String)

    val isDownloading: StateFlow<Boolean>
}

/** 브라우저로 넘겨도 되는 주소인지 — http·https 만 넘긴다. */
internal fun isWebUrl(url: String): Boolean {
    val lower = url.trim().lowercase()
    return lower.startsWith("https://") || lower.startsWith("http://")
}

/**
 * 강제 업데이트가 받아서 설치해도 되는 주소인지 — https 이고, 쿼리·조각(`?`·`#`)을 뺀 경로가 [extension] 으로 끝난다.
 * `x.html#.apk` 처럼 조각 뒤에 확장자를 붙인 주소는 설치 주소가 아니다.
 */
internal fun isInstallableUpdateUrl(url: String, extension: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return false
    val path = trimmed.substringBefore('#').substringBefore('?')
    return path.endsWith(extension, ignoreCase = true)
}

/** 플랫폼마다 강제 업데이트가 받아서 설치하는 패키지 확장자와, 스토어 앱으로 넘기는 주소의 머리. */
internal enum class UpdateRule(val packageExtension: String, val storePrefix: String) {
    ANDROID(".apk", "market://details?id="),
    IOS(".plist", "itms-apps://"),
}

/** 강제 업데이트 주소를 여는 방법. */
internal enum class UpdateUrlTarget {
    /** 받아서 설치 화면을 띄운다. */
    INSTALL,

    /** 브라우저나 스토어 앱으로 넘긴다. */
    VIEW,

    /** 열지 않는다. */
    NONE,
}

/**
 * 강제 업데이트 주소를 어떻게 열지 정한다.
 *
 * 설치 주소([isInstallableUpdateUrl])는 받아서 설치한다. 그 밖의 http·https 와 이 플랫폼의 스토어 주소([UpdateRule.storePrefix])는
 * 넘긴다. 나머지 scheme(`intent://`·`file://`·다른 플랫폼의 스토어 주소)은 열지 않는다.
 */
internal fun updateUrlTarget(url: String, rule: UpdateRule): UpdateUrlTarget = when {
    isInstallableUpdateUrl(url, rule.packageExtension) -> UpdateUrlTarget.INSTALL
    isWebUrl(url) || url.trim().startsWith(rule.storePrefix, ignoreCase = true) -> UpdateUrlTarget.VIEW
    else -> UpdateUrlTarget.NONE
}
