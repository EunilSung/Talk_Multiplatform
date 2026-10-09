package com.eunilsung.talk.server.local

import com.eunilsung.talk.server.config.DbConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 설치 없이 띄우는 PostgreSQL.
 *
 * 실행 파일이 의존성으로 내려와 임시 폴더에 풀린다. PC 에 PostgreSQL 이나 도커가 없어도
 * 운영과 같은 종류의 DB 로 개발하고 테스트할 수 있다.
 */
object LocalPostgres {

    /**
     * 개발용 — 데이터를 [dataDirectory] 에 남겨 서버를 다시 켜도 유지한다.
     *
     * 포트를 고정한다. 매번 바뀌면 DB 도구로 들여다볼 때마다 주소를 다시 찾아야 한다.
     */
    fun startPersistent(dataDirectory: File, port: Int): EmbeddedPostgres {
        stopLeftover(dataDirectory)
        return builder()
            .setDataDirectory(dataDirectory)
            .setCleanDataDirectory(false)
            .setPort(port)
            .start()
    }

    /**
     * 지난 실행이 남긴 DB 프로세스를 내린다.
     *
     * 서버를 강제로 끄면(IDE 정지 버튼, 작업 관리자) 종료 훅이 돌지 않아 DB 만 살아남는다.
     * 그대로 두면 포트와 데이터 폴더를 쥐고 있어 다음 실행이 뜨지 못한다.
     *
     * `postmaster.pid` 첫 줄이 그 프로세스 번호다. 번호가 다른 프로그램에 재사용됐을 수 있으므로
     * 실행 파일 이름이 postgres 일 때만 내린다.
     */
    private fun stopLeftover(dataDirectory: File) {
        val pid = File(dataDirectory, "postmaster.pid").takeIf { it.exists() }
            ?.useLines { it.firstOrNull() }
            ?.trim()
            ?.toLongOrNull()
            ?: return
        val process = ProcessHandle.of(pid).orElse(null) ?: return
        val isPostgres = process.info().command().orElse("").contains("postgres", ignoreCase = true)
        if (!isPostgres) return
        process.destroy()
        process.onExit().get(LEFTOVER_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** 테스트용 — 빈 DB 를 임시 폴더에 띄운다. 닫으면 사라진다. */
    fun startTemporary(): EmbeddedPostgres = builder().start()

    fun configOf(postgres: EmbeddedPostgres): DbConfig = DbConfig(
        url = postgres.getJdbcUrl(USER, DATABASE),
        user = USER,
        password = "",
    )

    /** DB 메시지를 영문으로 고정한다 — OS 언어를 따라가면 콘솔 인코딩에 따라 로그가 깨진다. */
    private fun builder(): EmbeddedPostgres.Builder =
        EmbeddedPostgres.builder().setLocaleConfig("lc-messages", "C")

    private const val USER = "postgres"
    private const val DATABASE = "postgres"
    private const val LEFTOVER_STOP_TIMEOUT_SECONDS = 10L
}
