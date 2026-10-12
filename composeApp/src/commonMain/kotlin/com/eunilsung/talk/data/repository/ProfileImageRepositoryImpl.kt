package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.data.local.ProfilePhotoStore
import com.eunilsung.talk.data.local.seed.TestAccounts
import com.eunilsung.talk.data.local.seed.TestProfileImages
import com.eunilsung.talk.domain.model.ProfileImageUrls
import com.eunilsung.talk.domain.repository.ProfileImageRepository
import com.eunilsung.talk.util.CompressedImage
import com.eunilsung.talk.util.compressImageToJpeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 프로필 사진 저장소.
 *
 * 다루는 사람은 시연 계정(test1~test10)과 지금 로그인한 계정이다. 서버도 이 계정들만 만든다.
 * 고른 사진은 압축해 기기 캐시에 쓰고 경로를 [ProfilePhotoStore] 에 남긴다.
 */
class ProfileImageRepositoryImpl(
    private val files: FileMetadataResolver,
) : ProfileImageRepository {

    private val _imageUrls = MutableStateFlow(load())
    override val imageUrls: StateFlow<ProfileImageUrls> = _imageUrls.asStateFlow()

    override suspend fun saveMyPhoto(sourceUri: String): String? {
        val bytes = runCatching { files.readBytes(sourceUri) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val compressed = runCatching { compressImageToJpeg(bytes, MAX_PHOTO_BYTES) }
            .getOrElse { CompressedImage(bytes, "") }
        val myId = Config.MyInfo.userId
        val saved = runCatching {
            files.writeCacheFile(ProfilePhotoStore.nextFileName(myId), compressed.bytes)
        }.getOrNull() ?: return null
        ProfilePhotoStore.save(myId, saved)
        _imageUrls.value = load()
        return saved
    }

    private fun load(): ProfileImageUrls {
        val userIds = (TestAccounts.ALL.map { it.userId } + Config.MyInfo.userId)
            .filter { it.isNotBlank() }
            .distinct()
        return ProfileImageUrls.of(
            userIds.mapNotNull { id ->
                (ProfilePhotoStore.pathFor(id) ?: TestProfileImages.uriFor(id))?.let { id to it }
            }.toMap()
        )
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 1_000_000
    }
}
