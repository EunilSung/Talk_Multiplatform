package com.eunilsung.talk.data.testdata

import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.domain.repository.UserProfileRepository

/** [TestAccounts] 를 그대로 프로필로 돌려주는 저장소. 미등록 id 는 id 만 채운 최소 프로필. */
class LocalUserProfileRepositoryImpl : UserProfileRepository {

    override suspend fun getCachedProfile(userId: String): UserProfile? = profileOf(userId)

    override suspend fun fetchProfile(userId: String): Result<UserProfile> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("userId is blank"))
        return Result.success(profileOf(userId) ?: UserProfile(userId = userId, name = userId))
    }

    private fun profileOf(userId: String): UserProfile? {
        val account = TestAccounts.find(userId) ?: return null
        return UserProfile(
            userId = account.userId,
            name = account.userName,
            position = account.positionName,
            department = account.organName,
            mobileNum = account.phoneNumber,
            email = account.email,
            motto = account.statusMessage,
            // 내선/팩스는 테스트 계정에 없는 값.
            extensionNum = "",
            localCallNum = "",
            faxNum = "",
            companyId = Config.MyInfo.companyCode,
        )
    }
}
