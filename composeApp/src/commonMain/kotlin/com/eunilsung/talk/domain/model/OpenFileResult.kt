package com.eunilsung.talk.domain.model

/** 받은 파일을 열어 본 결과. */
enum class OpenFileResult {
    /** 앱 캐시의 사본으로 열었다. */
    OPENED,

    /** 받지 못했거나 열 앱이 없다. */
    FAILED,
}
