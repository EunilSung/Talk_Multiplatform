package com.eunilsung.talk.testsupport

import com.eunilsung.talk.Config

/**
 * [Config.MyInfo] 는 전역 가변 상태라 케이스 사이에 값이 새어 나간다.
 * 각 테스트가 @BeforeTest 로 [loginAs], @AfterTest 로 [clear] 를 불러 격리한다.
 */
object TestMyInfo {

    const val ME = "test1"

    fun loginAs(userId: String = ME, userName: String = "김민준") {
        clear()
        Config.MyInfo.userId = userId
        Config.MyInfo.userName = userName
    }

    fun clear() {
        Config.MyInfo.userId = ""
        Config.MyInfo.userName = ""
        Config.MyInfo.email = ""
        Config.MyInfo.organName = ""
        Config.MyInfo.positionName = ""
    }
}
