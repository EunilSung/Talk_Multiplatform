package com.eunilsung.talk.functest

import com.eunilsung.talk.db.AppDatabase
import io.ktor.client.HttpClient

/**
 * 기능 테스트가 붙을 서버 주소(`TALK_FUNCTEST_URL`). 없으면 null — 기능 테스트는 돌지 않는다.
 *
 * 앱에 박힌 주소(`Config.Server.BASE_URL`)를 쓰지 않는다. 그 주소는 에뮬레이터에서 본 주소라
 * 테스트가 도는 자리에서는 닿지 않고, 무엇보다 평소 테스트가 서버로 나가면 안 된다.
 */
expect fun functionalTestServerUrl(): String?

/** 앱이 쓰는 것과 같은 엔진과 설정의 HTTP 클라이언트. */
expect fun functionalTestHttpClient(): HttpClient

/**
 * 기능 테스트용 기기 DB.
 *
 * 진짜 서버에 붙으면 내 요청의 결과와 서버 알림이 서로 다른 스레드에서 동시에 DB 에 쓴다. 다른 테스트가
 * 쓰는 메모리 DB 는 연결이 하나뿐이라 두 쓰기가 겹치면 깨진다. 기기의 실제 DB 처럼 동시에 쓸 수 있는
 * DB 를 준다.
 */
expect fun createFunctionalTestDatabase(): AppDatabase
