package com.eunilsung.talk.functest

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.eunilsung.talk.db.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

actual fun functionalTestServerUrl(): String? = System.getenv("TALK_FUNCTEST_URL")?.takeIf { it.isNotBlank() }

actual fun functionalTestHttpClient(): HttpClient = HttpClient(OkHttp) { install(WebSockets) }

actual fun createFunctionalTestDatabase(): AppDatabase {
    val driver = SerializedDriver(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
    AppDatabase.Schema.create(driver)
    return AppDatabase(driver)
}

/**
 * 한 번에 한 스레드만 DB 를 쓰게 줄을 세운다.
 *
 * 메모리 DB 드라이버는 연결도 트랜잭션 자리도 하나뿐이다. 두 스레드가 동시에 트랜잭션을 열면
 * "트랜잭션 안에서 트랜잭션을 시작할 수 없다"로 깨진다. 기기의 실제 DB 는 쓰기를 차례로 기다리게
 * 하므로, 여기서도 트랜잭션이 끝날 때까지 다른 스레드를 기다리게 해 같은 결과를 낸다.
 */
private class SerializedDriver(private val delegate: SqlDriver) : SqlDriver by delegate {

    private val lock = ReentrantLock()

    /** 트랜잭션은 연 스레드에서 끝난다. 열 때 잡은 자물쇠를 끝날 때(성공이든 되돌림이든) 푼다. */
    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        lock.lock()
        val transaction = try {
            delegate.newTransaction().value
        } catch (error: Throwable) {
            lock.unlock()
            throw error
        }
        transaction.afterCommit { lock.unlock() }
        transaction.afterRollback { lock.unlock() }
        return QueryResult.Value(transaction)
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = lock.withLock { delegate.execute(identifier, sql, parameters, binders) }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = lock.withLock { delegate.executeQuery(identifier, sql, mapper, parameters, binders) }
}
