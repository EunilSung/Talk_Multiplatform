package com.eunilsung.talk.server.config

import com.eunilsung.talk.server.limits.ServerLimits

/**
 * 서버 설정 — 전부 환경변수에서 읽는다.
 *
 * 비밀번호 같은 값을 파일에 적지 않는다. 배포 환경은 환경변수만 갈아 끼우면 된다.
 */
data class ServerConfig(
    val port: Int,
    /** 받을 주소(`HOST`). 운영은 컨테이너 안이라 모든 주소(0.0.0.0)에서 받고, 밖으로는 Caddy 만 연다. */
    val host: String = DEFAULT_HOST,
    val db: DbConfig,
    /** 올라온 파일을 두는 폴더. 컨테이너에서는 볼륨을 여기에 붙여야 재배포해도 파일이 남는다. */
    val filesDirectory: String,
    /**
     * Firebase 서비스 계정 키(JSON 본문). 비면 푸시를 보내지 않고 나머지는 그대로 동작한다.
     *
     * `FIREBASE_CREDENTIALS`(파일 경로, 로컬용) 또는 `FIREBASE_CREDENTIALS_B64`(같은 JSON 을 base64 로 감싼 값,
     * 컨테이너용)로 받는다. 여러 줄짜리 private key 가 셸을 거치며 깨지지 않게 한 줄로 감싼 것이다.
     */
    val firebaseCredentialsJson: String = "",
    /** Gemini API 키(`GEMINI_API_KEY`). 비면 AI 가 답하지 않고 나머지는 그대로 동작한다. */
    val aiApiKey: String = "",
    /** AI 가 쓸 모델(`AI_MODEL`). 무료 한도가 넉넉한 가벼운 모델을 기본으로 둔다. */
    val aiModel: String = DEFAULT_AI_MODEL,
    /** 한 사람이 한 시간에 AI 를 부를 수 있는 횟수(`AI_PER_USER_HOURLY`). 비면 [com.eunilsung.talk.server.ai.AiUsageLimiter] 의 기본값이다. */
    val aiPerUserHourly: Int? = null,
    /**
     * 시연용 계정(test1~test10)의 비밀번호. null 이면 계정을 만들지 않는다.
     *
     * 운영 진입점은 `SEED_DEMO_ACCOUNTS=true` 이고 `SEED_PASSWORD` 가 있을 때만 값이 생긴다([seedPasswordOf]). 기본 비밀번호는 없다.
     */
    val seedPassword: String? = null,
    /** 한 사람이 쓸 수 있는 서버 자원의 한도. 운영은 기본값이고, 로컬 개발 서버는 넉넉한 값을 넣는다. */
    val limits: ServerLimits = ServerLimits(),
) {
    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_HOST = "0.0.0.0"
        const val DEFAULT_AI_MODEL = "gemini-3.5-flash-lite"

        fun aiApiKeyFromEnv(): String = env("GEMINI_API_KEY").orEmpty()

        fun aiModelFromEnv(): String = env("AI_MODEL") ?: DEFAULT_AI_MODEL

        fun aiPerUserHourlyFromEnv(): Int? = env("AI_PER_USER_HOURLY")?.toIntOrNull()

        /** 시연용 계정을 켜는 값이 `true` 이고 비밀번호가 있을 때만 그 비밀번호, 아니면 null. */
        fun seedPasswordOf(demoAccounts: String?, password: String?): String? =
            password?.takeIf { it.isNotBlank() && demoAccounts?.trim().equals("true", ignoreCase = true) }

        fun fromEnv(): ServerConfig = ServerConfig(
            port = env("PORT")?.toIntOrNull() ?: DEFAULT_PORT,
            host = env("HOST") ?: DEFAULT_HOST,
            db = DbConfig(
                url = env("DB_URL") ?: "jdbc:postgresql://localhost:5432/talk_dev",
                user = env("DB_USER") ?: "talk",
                password = env("DB_PASSWORD") ?: "talk_local_dev",
                poolSize = env("DB_POOL_SIZE")?.toIntOrNull() ?: DbConfig.DEFAULT_POOL_SIZE,
            ),
            filesDirectory = env("FILES_DIR") ?: "files",
            firebaseCredentialsJson = firebaseCredentialsFromEnv(),
            aiApiKey = aiApiKeyFromEnv(),
            aiModel = aiModelFromEnv(),
            aiPerUserHourly = aiPerUserHourlyFromEnv(),
            seedPassword = seedPasswordOf(env("SEED_DEMO_ACCOUNTS"), env("SEED_PASSWORD")),
        )

        private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

        /** base64 를 먼저 본다. 배포 환경의 값이 로컬 경로보다 우선이어야 한다. */
        fun firebaseCredentialsFromEnv(): String {
            env("FIREBASE_CREDENTIALS_B64")?.let { encoded ->
                return runCatching { String(java.util.Base64.getDecoder().decode(encoded.trim())) }.getOrDefault("")
            }
            return env("FIREBASE_CREDENTIALS")
                ?.let { path -> runCatching { java.io.File(path).readText() }.getOrDefault("") }
                .orEmpty()
        }
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
