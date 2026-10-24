package com.eunilsung.talk.server.files

/**
 * 사용자가 보낸 파일 이름을 저장해도 되는 모양으로 정리한다.
 *
 * 이 이름은 받는 사람 앱이 기기에 저장할 때 그대로 경로에 붙는다. `../../Library/…` 같은 이름이 그대로 나가면
 * 받는 앱의 내부 파일을 덮어쓸 수 있다. 앱도 같은 규칙으로 한 번 더 정리하지만, 서버가 먼저 막는다.
 */
object FileNames {

    /** 저장하는 이름의 최대 길이. */
    const val MAX_LENGTH = 255

    /**
     * 마지막 경로 조각만 남기고, 제어 문자·방향 바꾸기 문자와 `..` 를 `_` 로 바꾼다. 앞뒤 공백은 뗀다.
     * 남는 것이 없으면 빈 문자열이다 — 부르는 쪽이 거절하거나 기본 이름을 쓴다.
     */
    fun clean(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\')
            .map { if (it.isISOControl() || it in BIDI_CONTROLS) REPLACEMENT else it }
            .joinToString("")
            .replace(PARENT, REPLACEMENT.toString())
            .trim()
            .take(MAX_LENGTH)

    private const val REPLACEMENT = '_'
    private const val PARENT = ".."

    /** 글자 방향을 바꿔 `exe.jpg` 를 `gpj.exe` 처럼 보이게 하는 문자들. */
    private val BIDI_CONTROLS = ('‪'..'‮').toSet() + ('⁦'..'⁩').toSet()
}
