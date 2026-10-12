package com.eunilsung.talk.data.local.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.SeedMapper
import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.domain.repository.UserProfileRepository
import com.eunilsung.talk.data.local.seed.TestAccounts

/** [TestAccounts] 를 그대로 프로필로 돌려주는 저장소. 미등록 id 는 id 만 채운 최소 프로필. */
class LocalUserProfileRepositoryImpl(
    private val seedMapper: SeedMapper,
) : UserProfileRepository {

    override suspend fun getCachedProfile(userId: String): UserProfile? = profileOf(userId)

    override suspend fun fetchProfile(userId: String): Result<UserProfile> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("userId is blank"))
        return Result.success(profileOf(userId) ?: UserProfile(userId = userId, name = userId))
    }

    private fun profileOf(userId: String): UserProfile? =
        TestAccounts.find(userId)?.let { seedMapper.toProfile(it, Config.MyInfo.companyCode) }
}
