package com.eunilsung.talk

import com.eunilsung.talk.data.local.orientedWidthHeight
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 사진 크기는 **화면에 보이는 방향**으로 계산한다.
 *
 * 휴대폰 카메라는 세로로 찍어도 픽셀을 가로(4000×2250)로 저장하고 EXIF 방향만 붙인다. 픽셀 크기를 그대로 쓰면
 * 말풍선의 가로·세로가 뒤집힌다.
 */
class OrientedWidthHeightTest {

    @Test
    fun 세로로_찍어_90도나_270도_회전이_붙은_사진은_가로와_세로를_바꾼다() {
        assertEquals("2250:4000", orientedWidthHeight(4000, 2250, exifOrientation = 6))
        assertEquals("2250:4000", orientedWidthHeight(4000, 2250, exifOrientation = 8))
        assertEquals("2250:4000", orientedWidthHeight(4000, 2250, exifOrientation = 5), "전치")
        assertEquals("2250:4000", orientedWidthHeight(4000, 2250, exifOrientation = 7), "횡단")
    }

    @Test
    fun 회전이_없거나_뒤집기만_한_사진은_그대로다() {
        (0..4).forEach { orientation ->
            assertEquals("4000:2250", orientedWidthHeight(4000, 2250, orientation), "EXIF 방향 $orientation")
        }
        assertEquals("4000:2250", orientedWidthHeight(4000, 2250, exifOrientation = 99), "알 수 없는 값")
    }

    @Test
    fun 크기를_모르면_빈_값이다() {
        assertEquals("", orientedWidthHeight(0, 2250, exifOrientation = 6))
        assertEquals("", orientedWidthHeight(4000, -1, exifOrientation = 1))
    }
}
