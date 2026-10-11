package com.eunilsung.talk.domain.repository

/** 다른 앱이 공유로 넘겨준 파일을 다 쓴 뒤 치운다. 치울 것이 없는 플랫폼에서는 아무 일도 하지 않는다. */
fun interface SharedFileCleaner {
    fun cleanup(filePath: String)
}
