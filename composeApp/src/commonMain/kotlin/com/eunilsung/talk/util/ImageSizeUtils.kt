package com.eunilsung.talk.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 채팅 이미지 표시 크기 계산 유틸 — 비율 유지하며 [MAX_DIMENSION] dp 안에 맞춤. */
object ImageSizeUtils {

    /** 더 긴 변의 최대 표시 크기 (dp). */
    private const val MAX_DIMENSION = 240

    /** 비율 유지된 표시 크기. */
    data class ImageSize(val width: Dp, val height: Dp)

    /**
     * "width:height" 를 파싱해 표시용 크기 계산.
     * @return [ImageSize], opt2 가 null/빈/형식 오류면 null.
     */
    fun getImageSize(opt2: String?): ImageSize? {
        if (opt2.isNullOrBlank()) return null
        val parts = opt2.split(":")
        if (parts.size != 2) return null
        val origW = parts[0].toIntOrNull() ?: return null
        val origH = parts[1].toIntOrNull() ?: return null
        if (origW <= 0 || origH <= 0) return null

        val max = MAX_DIMENSION
        val (w, h) = when {
            origW > origH -> {
                val newH = (origH.toDouble() * max / origW.toDouble()).toInt()
                max to newH
            }
            origW < origH -> {
                val newW = (origW.toDouble() * max / origH.toDouble()).toInt()
                newW to max
            }
            else -> max to max
        }
        return ImageSize(width = w.dp, height = h.dp)
    }
}
