package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.ProfileImageUrls
import kotlinx.coroutines.flow.StateFlow

/** 프로필 사진 — 사람마다 보여 줄 사진과, 내가 직접 고른 사진. 사진은 이 기기에만 둔다. */
interface ProfileImageRepository {
    /** 사람마다 보여 줄 사진의 주소. 내 사진을 바꾸면 새 값이 흐른다. */
    val imageUrls: StateFlow<ProfileImageUrls>

    /** 내 프로필 사진을 [sourceUri] 의 사진으로 바꾼다. 바꾼 사진의 주소, 읽거나 저장하지 못하면 null. */
    suspend fun saveMyPhoto(sourceUri: String): String?
}
