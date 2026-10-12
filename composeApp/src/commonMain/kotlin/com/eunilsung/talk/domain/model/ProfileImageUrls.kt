package com.eunilsung.talk.domain.model

/**
 * 사람마다 보여 줄 프로필 사진의 주소. 아이디의 대소문자는 가리지 않는다.
 *
 * 직접 고른 사진이 있으면 그것, 없으면 계정마다 준비된 기본 이미지다. 둘 다 없는 사람은 빈 문자열이다.
 */
class ProfileImageUrls private constructor(private val byUserId: Map<String, String>) {

    fun urlFor(userId: String): String = byUserId[key(userId)].orEmpty()

    override fun equals(other: Any?): Boolean = other is ProfileImageUrls && other.byUserId == byUserId

    override fun hashCode(): Int = byUserId.hashCode()

    companion object {
        val EMPTY = ProfileImageUrls(emptyMap())

        fun of(urlsByUserId: Map<String, String>): ProfileImageUrls =
            ProfileImageUrls(urlsByUserId.mapKeys { key(it.key) })

        private fun key(userId: String) = userId.trim().lowercase()
    }
}
