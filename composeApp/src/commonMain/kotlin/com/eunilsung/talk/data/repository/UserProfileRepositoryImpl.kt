package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.remote.server.UserDirectory
import com.eunilsung.talk.data.remote.server.valueOrNull
import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.domain.repository.UserProfileRepository
import com.eunilsung.talk.shared.api.UserDto

/** 서버에서 사용자 프로필을 받는 저장소. 마지막으로 받은 사용자 목록([UserDirectory])을 캐시로 쓴다. */
class UserProfileRepositoryImpl(
    private val server: TalkServer,
    private val directory: UserDirectory,
) : UserProfileRepository {

    override suspend fun getCachedProfile(userId: String): UserProfile? = directory.find(userId)?.toProfile()

    /** 서버에서 새로 받는다. 못 받으면 가지고 있던 것을, 그것도 없으면 실패를 돌려준다. */
    override suspend fun fetchProfile(userId: String): Result<UserProfile> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("userId is blank"))
        val fresh = server.user(userId).valueOrNull()?.also(directory::put)
        val user = fresh ?: directory.find(userId)
            ?: return Result.failure(IllegalStateException("프로필을 받지 못했다"))
        return Result.success(user.toProfile())
    }

    private fun UserDto.toProfile() = UserProfile(
        userId = id,
        name = name,
        position = positionName,
        department = organName,
        mobileNum = phoneNumber,
        email = email,
        motto = statusMessage,
        companyId = Config.MyInfo.companyCode,
    )
}
