package com.eunilsung.talk.domain.util

/**
 * 기기가 직접 받아 와도 되는 링크 미리보기 주소인지.
 *
 * 대화 링크는 누구나 보낼 수 있다. 미리보기는 그 주소에 기기가 직접 GET 을 보내므로, 받는 사람의 내부망(사내 Wi-Fi 의
 * 공유기·사설 서버)이나 기기 자신(루프백)으로 요청이 가지 않게 막는다.
 *
 * - https 만 받는다. http 는 중간에서 바뀔 수 있다.
 * - 호스트는 ASCII 영문·숫자·`-`·`.` 로 된 점이 하나 이상 있는 이름이거나 공인 IPv4 여야 한다. `%` 인코딩·전각 숫자·
 *   한글 도메인은 HTTP 라이브러리가 숫자 주소로 풀 수 있어 받지 않는다.
 * - `localhost`·`.local`·`.internal`·`.lan`·`.home.arpa`·`.localdomain` 과 점 없는 이름(사내망 이름)은 받지 않는다.
 * - 숫자 주소는 점 넷으로 쓴 IPv4 만 받고, 사설·루프백·링크로컬·CGNAT·멀티캐스트·예약 대역은 받지 않는다.
 *   `0x7f.1`·`127.1` 처럼 줄여 쓴 숫자 주소와 IPv6 주소는 받지 않는다.
 *
 * 이름이 사설 주소로 풀리는 경우(DNS)는 여기서 알 수 없다. 그 대비는 서버가 대신 받아 주는 구조로 바꿀 때 한다.
 */
fun isPreviewableUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith(HTTPS_PREFIX, ignoreCase = true)) return false
    val authority = trimmed.substring(HTTPS_PREFIX.length).takeWhile { it !in AUTHORITY_END }
    if ('@' in authority || authority.startsWith("[")) return false
    val host = authority.substringBefore(':').lowercase().removeSuffix(".")
    val port = authority.substringAfter(':', "")
    if (port.isNotEmpty() && port.any { !it.isDigit() }) return false
    return isPublicHost(host)
}

private fun isPublicHost(host: String): Boolean {
    if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return false
    if (host.any { it !in HOST_CHARS }) return false
    val labels = host.split('.')
    if (labels.size < 2 || labels.any { it.isEmpty() }) return false
    if (labels.last().isNumericLabel()) return isPublicIpv4(labels)
    if (host == LOCALHOST || LOCAL_SUFFIXES.any { host == it || host.endsWith(".$it") }) return false
    return true
}

/** 숫자 주소로 읽힐 수 있는 조각 — 10진·8진(`0` 으로 시작)·16진(`0x`). */
private fun String.isNumericLabel(): Boolean =
    all { it.isDigit() } || (startsWith("0x") && drop(2).all { it.isDigit() || it in 'a'..'f' })

private fun isPublicIpv4(labels: List<String>): Boolean {
    if (labels.size != 4) return false
    val octets = labels.map { label ->
        if (label.isEmpty() || label.length > 3 || !label.all { it.isDigit() }) return false
        if (label.length > 1 && label.startsWith('0')) return false
        label.toInt().takeIf { it in 0..255 } ?: return false
    }
    val (a, b) = octets
    return when {
        a == 0 || a == 10 || a == 127 -> false
        a == 100 && b in 64..127 -> false
        a == 169 && b == 254 -> false
        a == 172 && b in 16..31 -> false
        a == 192 && b == 168 -> false
        a == 192 && b == 0 && octets[2] == 0 -> false
        a == 198 && b in 18..19 -> false
        a >= 224 -> false
        else -> true
    }
}

private const val HTTPS_PREFIX = "https://"
private const val LOCALHOST = "localhost"
private const val MAX_HOST_LENGTH = 253
private val AUTHORITY_END = setOf('/', '\\', '?', '#')
private val HOST_CHARS = ('a'..'z').toSet() + ('0'..'9').toSet() + setOf('-', '.')
private val LOCAL_SUFFIXES = listOf("localhost", "local", "internal", "lan", "home.arpa", "localdomain")
