package com.eunilsung.talk.util

/** 사용자 검색 매칭 — 이름(부분 일치 + 초성)과 전화 숫자 동시 지원. */
object UserSearch {

    private val CHOSUNG = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    /** 문자열의 각 한글 음절을 초성으로 변환(비한글 문자는 그대로 유지). */
    private fun chosung(text: String): String = buildString {
        for (ch in text) {
            val code = ch.code
            if (code in 0xAC00..0xD7A3) append(CHOSUNG[(code - 0xAC00) / 588])
            else append(ch)
        }
    }

    /** 검색어가 초성 자모(+공백)로만 이루어졌는지 — 이면 초성 검색으로 판단. */
    private fun isChosungQuery(q: String): Boolean =
        q.isNotEmpty() && q.all { it == ' ' || CHOSUNG.contains(it) }

    private fun matchesName(name: String, query: String): Boolean {
        if (name.contains(query, ignoreCase = true)) return true
        if (isChosungQuery(query)) {
            val nameCho = chosung(name).replace(" ", "")
            val q = query.replace(" ", "")
            if (q.isNotEmpty() && nameCho.contains(q)) return true
        }
        return false
    }

    /** phone("local|내선|핸드폰|팩스")의 각 슬롯 숫자에서 검색어 숫자를 부분 일치. */
    private fun matchesPhone(phone: String?, query: String): Boolean {
        if (phone.isNullOrBlank()) return false
        val q = query.filter { it.isDigit() }
        if (q.length < 2) return false
        return phone.split('|').any { slot ->
            slot.filter { it.isDigit() }.contains(q)
        }
    }

    /**
     * @param name 사용자 이름(한글)
     * @param phone `local|내선|핸드폰|팩스` 복합 전화 문자열
     * @param query 검색어 (이름 일부/초성 또는 전화번호 숫자)
     */
    fun matches(name: String?, phone: String?, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (name != null && matchesName(name, q)) return true
        if (matchesPhone(phone, q)) return true
        return false
    }
}
