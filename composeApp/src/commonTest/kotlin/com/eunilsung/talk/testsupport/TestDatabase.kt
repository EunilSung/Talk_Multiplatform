package com.eunilsung.talk.testsupport

import com.eunilsung.talk.db.AppDatabase

/** 테스트 전용 인메모리 DB. 호출마다 새 DB 라 케이스끼리 간섭하지 않는다. */
expect fun createTestDatabase(): AppDatabase
