package com.eunilsung.talk.server.db

import com.eunilsung.talk.server.testsupport.TestDatabase
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * V10 이 이미 배포된 DB 의 옛 행을 채우는지 본다.
 *
 * 테스트 DB 는 빈 상태에서 마이그레이션을 돌려 채울 행이 없다. 옛 모양의 행을 넣은 뒤 그 파일의 UPDATE 문만 다시 돌린다.
 */
class MigrationBackfillTest {

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `옛 파일 대화는 그 방에 올라온 파일만 가리키고 옛 투표는 알림 대화의 번호를 얻는다`() {
        val room = UUID.randomUUID().toString()
        val otherRoom = UUID.randomUUID().toString()
        val fileId = UUID.randomUUID().toString()
        val voteId = UUID.randomUUID().toString()
        execute(
            "INSERT INTO chat_room (id) VALUES ('$room'), ('$otherRoom')",
            "INSERT INTO chat_file (id, room_id, uploader_id, name, size) VALUES ('$fileId', '$room', 'test1', 'a.jpg', 1)",
            "INSERT INTO chat_message (room_id, seq, client_id, sender_id, kind, payload) VALUES " +
                "('$room', 1, 'f1', 'test1', 'image', '{\"fileId\":\"$fileId\"}'), " +
                "('$room', 2, 'v1', 'test1', 'vote', '{\"vote\":{\"id\":\"$voteId\",\"title\":\"t\"}}'), " +
                "('$otherRoom', 1, 'f2', 'test1', 'file', '{\"fileId\":\"$fileId\"}'), " +
                "('$room', 3, 't1', 'test1', 'text', '{\"fileId\":\"$fileId\"}')",
            "INSERT INTO chat_vote (id, room_id, title, writer_id) VALUES ('$voteId', '$room', 't', 'test1')",
        )

        execute(*backfillStatements().toTypedArray())

        assertEquals(fileId, single("SELECT file_id FROM chat_message WHERE client_id = 'f1'"))
        assertNull(single("SELECT file_id FROM chat_message WHERE client_id = 'f2'"))
        assertNull(single("SELECT file_id FROM chat_message WHERE client_id = 't1'"))
        assertEquals("2", single("SELECT seq FROM chat_vote WHERE id = '$voteId'"))
    }

    /** 마이그레이션 파일의 UPDATE 문들. 주석 줄은 뗀다. */
    private fun backfillStatements(): List<String> {
        val sql = checkNotNull(javaClass.classLoader.getResource(MIGRATION)).readText()
        return sql.split(';')
            .map { statement -> statement.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n").trim() }
            .filter { it.startsWith("UPDATE") }
    }

    private fun execute(vararg statements: String) {
        TestDatabase.dataSource.connection.use { conn ->
            conn.createStatement().use { st -> statements.forEach(st::execute) }
        }
    }

    private fun single(sql: String): String? =
        TestDatabase.dataSource.connection.use { conn ->
            conn.createStatement().use { st -> st.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null } }
        }

    private companion object {
        const val MIGRATION = "db/migration/V10__message_file_vote_seq.sql"
    }
}
