package com.eunilsung.talk.data.local

/** 동영상 경로에서 첫 프레임 이미지와 재생시간을 뽑는다. */
interface VideoThumbnailLoader {
    /** 실패 시 예외 대신 frameBytes = null. */
    suspend fun load(path: String): VideoThumb
}

/**
 * @param frameBytes 첫 프레임 JPEG 바이트. 실패 시 null.
 * @param durationSec 재생 길이(초). 모르면 0.
 * @param widthHeight 원본 해상도 `"가로:세로"`. 모르면 빈 문자열.
 */
data class VideoThumb(
    val frameBytes: ByteArray? = null,
    val durationSec: Long = 0,
    val widthHeight: String = "",
)
