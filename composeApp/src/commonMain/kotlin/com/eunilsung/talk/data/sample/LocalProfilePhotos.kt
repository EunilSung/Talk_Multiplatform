package com.eunilsung.talk.data.sample

import com.russhwolf.settings.Settings

/** 사용자가 직접 고른 프로필 사진의 로컬 경로 보관소. */
object LocalProfilePhotos {

    private const val PATH_KEY = "local.profile.photo."
    private const val SEQ_KEY = "local.profile.photo.seq."

    private val settings: Settings by lazy { Settings() }

    /** 직접 지정한 사진의 로컬 경로, 없으면 null. */
    fun pathFor(userId: String): String? =
        settings.getStringOrNull(PATH_KEY + userId)?.takeIf { it.isNotBlank() }

    /** 다음에 저장할 캐시 파일명(호출마다 새 이름 — Coil 캐시 우회). */
    fun nextFileName(userId: String): String {
        val seq = settings.getInt(SEQ_KEY + userId, 0) + 1
        settings.putInt(SEQ_KEY + userId, seq)
        return "profile_${userId}_$seq.jpg"
    }

    fun save(userId: String, localPath: String) {
        settings.putString(PATH_KEY + userId, localPath)
    }
}
