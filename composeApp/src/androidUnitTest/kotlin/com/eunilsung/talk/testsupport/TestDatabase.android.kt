package com.eunilsung.talk.testsupport

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.eunilsung.talk.db.AppDatabase

/** JVM 유닛테스트라 실기기 없이 도는 JDBC 인메모리 드라이버를 쓴다. */
actual fun createTestDatabase(): AppDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    AppDatabase.Schema.create(driver)
    return AppDatabase(driver)
}
