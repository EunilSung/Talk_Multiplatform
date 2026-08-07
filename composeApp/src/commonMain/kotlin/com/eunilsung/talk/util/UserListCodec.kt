package com.eunilsung.talk.util

/** 대화방 참여자 목록의 문자열 표현 — `"id|name;id|name;…"` 인코딩/디코딩. */
object UserListCodec {

    private const val ENTRY_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = "|"

    /** `"id|name;id|name"` → `(id, name)` 목록. id 가 빈 항목은 버린다. */
    fun decode(raw: String): List<Pair<String, String>> =
        raw.split(ENTRY_SEPARATOR).filter { it.isNotBlank() }.mapNotNull { entry ->
            val parts = entry.split(FIELD_SEPARATOR)
            val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to parts.getOrNull(1).orEmpty()
        }

    /** `(id, name)` 목록 → `"id|name;id|name"`. */
    fun encode(users: List<Pair<String, String>>): String =
        users.joinToString(ENTRY_SEPARATOR) { (id, name) -> "$id$FIELD_SEPARATOR$name" }

    /** 참여자 id 만. */
    fun decodeIds(raw: String): List<String> = decode(raw).map { it.first }

    /** [userId] 가 참여자로 들어 있는지. */
    fun contains(raw: String, userId: String): Boolean =
        userId.isNotBlank() && decodeIds(raw).contains(userId)
}
