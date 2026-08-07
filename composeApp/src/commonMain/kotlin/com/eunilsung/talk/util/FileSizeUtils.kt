package com.eunilsung.talk.util

import kotlin.math.round

/** byte 크기를 KB / MB / GB 단위 문자열로 변환 (소수 1자리). [bytes] < 0 이면 빈 문자열. */
fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return ""
    val kb = bytes.toDouble() / 1024.0
    val (value, unit) = when {
        kb < 1024.0 -> kb to "KB"
        kb < 1024.0 * 1024.0 -> (kb / 1024.0) to "MB"
        else -> (kb / (1024.0 * 1024.0)) to "GB"
    }
    val tenths = round(value * 10.0).toLong()
    val whole = tenths / 10
    val frac = tenths % 10
    val number = if (frac == 0L) whole.toString() else "$whole.$frac"
    return "$number$unit"
}
