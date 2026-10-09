package com.eunilsung.talk.server.config

/**
 * 서버 설정 — 전부 환경변수에서 읽는다.
 *
 * 비밀번호 같은 값을 파일에 적지 않는다. 배포 환경은 환경변수만 갈아 끼우면 된다.
 */
data class ServerConfig(
    val port: Int,
    val db: DbConfig,
) {
    companion object {
        const val DEFAULT_PORT = 8080

        fun fromEnv(): ServerConfig = ServerConfig(
            port = env("PORT")?.toIntOrNull() ?: DEFAULT_PORT,
            db = DbConfig(
                url = env("DB_URL") ?: "jdbc:postgresql://localhost:5432/talk_dev",
                user = env("DB_USER") ?: "talk",
                password = env("DB_PASSWORD") ?: "talk_local_dev",
                poolSize = env("DB_POOL_SIZE")?.toIntOrNull() ?: DbConfig.DEFAULT_POOL_SIZE,
            ),
        )

        private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}

/**
 * DB 연결 설정.
 *
 * [poolSize] 는 기계마다 다르다. 커넥션 하나가 PostgreSQL 쪽 프로세스 하나라, 작은 인스턴스에서는
 * 줄여야 하고 큰 기계에서는 늘려야 요청이 줄을 서지 않는다. 그래서 배포가 정한다.
 */
data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int = DEFAULT_POOL_SIZE,
) {
    companion object {
        const val DEFAULT_POOL_SIZE = 10
    }
}
