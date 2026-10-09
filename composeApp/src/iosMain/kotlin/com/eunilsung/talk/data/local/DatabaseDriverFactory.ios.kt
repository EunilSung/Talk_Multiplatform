package com.eunilsung.talk.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseFileContext
import com.eunilsung.talk.Config
import com.eunilsung.talk.db.AppDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = NativeSqliteDriver(AppDatabase.Schema, Config.Database.NAME)
        /**
         * DB 가 든 디렉터리를 백업에서 뺀다 — 이유는 [excludeFromBackup].
         *
         * 경로는 드라이버(SQLiter)에게 묻는다. 직접 `Application Support/databases` 를 적으면 라이브러리가
         * 위치를 바꿨을 때 엉뚱한 곳에 걸고 조용히 넘어간다. 드라이버를 만든 **뒤에** 거는 것은 디렉터리가
         * 그때 생기기 때문이다. 앱을 켤 때마다 다시 건다(이미 걸려 있으면 그대로다).
         */
        DatabaseFileContext.databasePath(Config.Database.NAME, null)
            .substringBeforeLast('/')
            .let(::excludeFromBackup)
        return driver
    }
}
