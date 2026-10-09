package com.eunilsung.talk.server.db

import com.eunilsung.talk.server.config.DbConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * 커넥션 풀 + 마이그레이션.
 *
 * 스키마의 정본은 `resources/db/migration` 의 SQL 파일이다. 코드가 표를 만들지 않는다 —
 * 그래야 로컬·테스트·운영이 같은 순서로 같은 스키마에 도달한다.
 */
object Database {

    fun connect(config: DbConfig): DataSource {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = config.poolSize
                connectionTimeout = CONNECTION_TIMEOUT_MS
                poolName = "talk-pool"
            }
        )
        migrate(dataSource)
        return dataSource
    }

    private fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    private const val CONNECTION_TIMEOUT_MS = 5_000L
}
