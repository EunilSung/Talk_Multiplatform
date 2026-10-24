package com.eunilsung.talk.util

/** 쓸 수 있는 이름이 남지 않을 때 대신 쓰는 파일 이름. */
const val FALLBACK_FILE_NAME = "file"

/**
 * 받은 파일 이름(보낸 사람이 정한 값)을 저장 폴더 안의 한 조각으로 만든다.
 *
 * - `/`·`\` 로 나눈 마지막 조각만 남긴다(`../../Library/x.plist` → `x.plist`).
 * - 제어 문자와 글자 방향을 바꾸는 문자(`U+202E` 등, 확장자를 속이는 데 쓴다)는 `_` 로 바꾼다.
 * - 남은 것이 비었거나 `.`·`..` 처럼 점뿐이면 [fallback] 을 쓴다.
 */
fun sanitizeFileName(name: String, fallback: String = FALLBACK_FILE_NAME): String {
    val lastSegment = name.substringAfterLast('/').substringAfterLast('\\')
    val cleaned = lastSegment.map { if (it.isUnsafeFileNameChar()) '_' else it }.joinToString("").trim()
    return if (cleaned.isEmpty() || cleaned.all { it == '.' }) fallback else cleaned
}

/**
 * [dir] 아래에 [name] 을 정리해([sanitizeFileName]) 붙인 경로.
 *
 * 붙인 경로를 `.`·`..` 를 풀어 표준화한 뒤 [dir] 안에 있을 때만 돌려준다. 정리한 이름은 한 조각이라 늘 안에 있지만,
 * 저장 직전에 한 번 더 막아 두는 검사다. [dir] 가 비었으면 null.
 */
fun childPathInside(dir: String, name: String): String? {
    val base = normalizePath(dir.trimEnd('/'))
    if (base.isEmpty()) return null
    val candidate = normalizePath("$base/${sanitizeFileName(name)}")
    return candidate.takeIf { it.startsWith("$base/") && it.length > base.length + 1 }
}

/** `/a/./b/../c` → `/a/c`. 루트 위로 올라가는 `..` 는 버린다. */
private fun normalizePath(path: String): String {
    val parts = ArrayDeque<String>()
    path.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(part)
        }
    }
    val joined = parts.joinToString("/")
    return if (path.startsWith('/')) "/$joined" else joined
}

private fun Char.isUnsafeFileNameChar(): Boolean = isISOControl() || this in BIDI_CONTROLS

private val BIDI_CONTROLS = setOf(
    '‎', '‏', '‪', '‫', '‬', '‭', '‮',
    '⁦', '⁧', '⁨', '⁩',
)
