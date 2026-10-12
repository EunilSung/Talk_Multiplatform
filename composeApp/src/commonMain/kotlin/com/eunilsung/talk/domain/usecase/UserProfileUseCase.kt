package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.domain.repository.UserProfileRepository

/** 프로필 화면의 유스케이스 묶음. */
data class UserProfileUseCases(
    val getCachedProfile: GetCachedProfileUseCase,
    val fetchProfile: FetchProfileUseCase,
    val getMyUser: GetMyUserUseCase,
)

/** 받아 둔 프로필. 없으면 null. */
class GetCachedProfileUseCase(private val repository: UserProfileRepository) {
    suspend operator fun invoke(userId: String): UserProfile? = repository.getCachedProfile(userId)
}

/** 프로필을 새로 받아 온다. */
class FetchProfileUseCase(private val repository: UserProfileRepository) {
    suspend operator fun invoke(userId: String): Result<UserProfile> = repository.fetchProfile(userId)
}
