package com.eunilsung.talk.testsupport

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.eunilsung.talk.db.AppDatabase

/** 인메모리 DB 도 이름이 같으면 공유돼 케이스끼리 섞인다. 매번 새 이름을 준다. */
private var sequence = 0

/** 파일을 남기지 않도록 native 드라이버를 inMemory 로 띄운다. */
actual fun createTestDatabase(): AppDatabase {
    val driver = NativeSqliteDriver(
        schema = AppDatabase.Schema,
        name = "test_${sequence++}.db",
        onConfiguration = { it.copy(inMemory = true) },
    )
    return AppDatabase(driver)
}
