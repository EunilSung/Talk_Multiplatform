package com.eunilsung.talk.server.testsupport

import com.eunilsung.talk.server.db.Database
import com.eunilsung.talk.server.local.LocalPostgres
import javax.sql.DataSource

/**
 * 테스트용 데이터베이스.
 *
 * 테스트 실행 한 번에 내장 PostgreSQL 하나를 띄워 모든 테스트가 나눠 쓴다. 운영과 같은 Flyway
 * 마이그레이션으로 표를 만들므로, 마이그레이션이 깨지면 여기서 먼저 드러난다.
 *
 * 개발용 DB(`server/.localdb`)와는 별개의 임시 인스턴스라 개발 데이터를 건드리지 않는다.
 */
object TestDatabase {

    val dataSource: DataSource by lazy {
        val postgres = LocalPostgres.startTemporary()
        Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
        Database.connect(LocalPostgres.configOf(postgres))
    }

    /**
     * 테스트 사이에 데이터를 비운다.
     *
     * 표는 남기고 내용만 지운다 — 마이그레이션을 매번 다시 돌리는 것보다 빠르다.
     * 표를 추가하면 여기에도 넣는다.
     *
     * 시연용 계정(app_user)은 남긴다. 비밀번호 해시가 일부러 느려서 매번 다시 만들면 테스트가 늘어진다.
     */
    fun clean() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.execute("TRUNCATE chat_message, chat_room_member, chat_room, auth_token RESTART IDENTITY CASCADE")
            }
        }
    }
}
