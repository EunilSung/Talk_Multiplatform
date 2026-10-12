package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.ProfileImageUrls
import com.eunilsung.talk.domain.repository.ProfileImageRepository
import kotlinx.coroutines.flow.StateFlow

/** 사람마다 보여 줄 프로필 사진의 주소. */
class ObserveProfileImagesUseCase(private val repository: ProfileImageRepository) {
    operator fun invoke(): StateFlow<ProfileImageUrls> = repository.imageUrls
}

/** 내 프로필 사진을 고른 사진으로 바꾼다. 바꾼 사진의 주소, 실패하면 null. */
class SaveMyProfilePhotoUseCase(private val repository: ProfileImageRepository) {
    suspend operator fun invoke(sourceUri: String): String? = repository.saveMyPhoto(sourceUri)
}
