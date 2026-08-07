package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.UserProfile

/** 사용자 상세 프로필 저장소. */
interface UserProfileRepository {
    /** 캐시된 프로필 1회 조회. 없으면 null. */
    suspend fun getCachedProfile(userId: String): UserProfile?

    /** 프로필을 새로 가져온다. */
    suspend fun fetchProfile(userId: String): Result<UserProfile>
}
