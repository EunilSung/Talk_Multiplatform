package com.eunilsung.talk.domain.util

/** 그룹 이름 검증 결과. */
sealed interface GroupNameValidation {
    /** 모든 검증 통과 */
    data object Valid : GroupNameValidation
    /** 빈 문자열 / 공백만 */
    data object Empty : GroupNameValidation
    /** 글자 수 초과 */
    data object TooLong : GroupNameValidation
    /** 사용 불가 특수문자 포함 */
    data object InvalidChars : GroupNameValidation
    /** 기존 그룹과 중복 */
    data object Duplicate : GroupNameValidation
}

/** 그룹 이름 최대 글자 수 */
const val GROUP_NAME_MAX_LENGTH = 14

/** 사용 불가 특수문자 집합. */
private val FORBIDDEN_CHARS: Set<Char> = setOf(
    '~', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
    '+', '|', '=', '[', ']', '{', '}', ';', ':', '\'', '"',
    '/', '?', '.', ',', '<', '>', '\\'
)

/**
 * 그룹 이름 검증.
 * @param existingNames 중복 비교 대상 (자기 자신 그룹은 제외).
 */
fun validateGroupName(
    newName: String,
    existingNames: Set<String>,
    maxLength: Int = GROUP_NAME_MAX_LENGTH
): GroupNameValidation {
    val trimmed = newName.trim()
    if (trimmed.isEmpty()) return GroupNameValidation.Empty
    if (trimmed.length > maxLength) return GroupNameValidation.TooLong
    if (trimmed.any { it in FORBIDDEN_CHARS }) return GroupNameValidation.InvalidChars
    if (trimmed in existingNames) return GroupNameValidation.Duplicate
    return GroupNameValidation.Valid
}
