package com.eunilsung.talk.data.testdata

import multiplatformtalk.composeapp.generated.resources.Res

/** 테스트 계정 프로필 이미지 — `user_1.png`~`user_9.png` 를 `test1`~`test9` 에 매핑. */
object TestProfileImages {

    /** 준비된 이미지 장수 — user_1.png ~ user_9.png */
    private const val IMAGE_COUNT = 9

    /** 매핑되는 테스트 계정이면 리소스 URI, 아니면 null. */
    fun uriFor(userId: String): String? {
        val account = TestAccounts.find(userId) ?: return null
        val index = account.userId.removePrefix("test").toIntOrNull() ?: return null
        if (index !in 1..IMAGE_COUNT) return null
        return Res.getUri("drawable/user_$index.png")
    }
}
