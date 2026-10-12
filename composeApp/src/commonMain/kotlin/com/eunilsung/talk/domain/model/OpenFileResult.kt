package com.eunilsung.talk.domain.model

/** 받은 파일을 열어 본 결과. */
enum class OpenFileResult {
    /** 전에 받아 둔 파일을 열었다. */
    OPENED,

    /** 처음 받아서 열었다. 받았다는 안내도 함께 한다. */
    DOWNLOADED_AND_OPENED,

    /** 받지 못했거나 열 앱이 없다. */
    FAILED,
}
