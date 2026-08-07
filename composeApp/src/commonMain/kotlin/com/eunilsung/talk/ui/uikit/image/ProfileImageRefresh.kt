package com.eunilsung.talk.ui.uikit.image

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.eunilsung.talk.data.testdata.LocalProfilePhotos
import com.eunilsung.talk.data.testdata.TestProfileImages

/** 프로필 이미지 강제 갱신 버전 — 사진 교체 시 [bump] 호출로 재구성 유발. */
object ProfileImageRefresh {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
    }
}

/** 프로필 이미지 경로. 우선순위: 직접 고른 사진 → 계정별 번들 이미지 → 없음. */
fun profileImageUrl(userId: String, version: Int): String {
    LocalProfilePhotos.pathFor(userId)?.let { return it }
    TestProfileImages.uriFor(userId)?.let { return it }
    return ""
}
