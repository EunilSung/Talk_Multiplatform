package com.eunilsung.talk.util

/** 압축 결과 — JPEG 바이트 + 최종 이미지 "width:height". */
data class CompressedImage(
    val bytes: ByteArray,
    val widthHeight: String,
)

/** 이미지를 [maxBytes] 이하 JPEG 로 압축. */
expect suspend fun compressImageToJpeg(bytes: ByteArray, maxBytes: Int): CompressedImage
