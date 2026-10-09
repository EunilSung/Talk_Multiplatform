package com.eunilsung.talk.server.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * 로그인 시도 제한.
 *
 * 한 아이디에 틀린 비밀번호가 연달아 [maxFailures] 번 들어오면 [lockMillis] 동안 그 아이디의 로그인을
 * 받지 않는다. 비밀번호를 하나씩 넣어 보는 공격이 느려진다.
 *
 * 아이디를 기준으로 센다. 주소를 기준으로 세면 프록시 뒤에서는 모든 사람이 한 주소로 보이고,
 * 공격하는 쪽은 주소를 바꿔 가며 피할 수 있다. 대신 남의 아이디를 일부러 잠글 수 있다는 약점이
 * 있어, 잠금 시간을 짧게 둔다.
 *
 * 기록은 메모리에만 둔다. 서버를 다시 띄우면 지워지는데, 그 정도 틈은 받아들인다 — DB 에 두면
 * 로그인할 때마다 쓰기가 한 번 더 생긴다.
 */
class LoginAttemptLimiter(
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
    private val lockMillis: Long = DEFAULT_LOCK_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class Record(val failures: Int, val lockedUntil: Long)

    private val records = ConcurrentHashMap<String, Record>()

    /** 지금 잠겨 있으면 풀릴 때까지 남은 초, 아니면 0. */
    fun secondsUntilUnlocked(userId: String): Long {
        val record = records[keyOf(userId)] ?: return 0
        val remaining = record.lockedUntil - now()
        if (remaining > 0) return (remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        /** 잠금이 끝났으면 기록을 지운다. 남겨 두면 다음 한 번의 실수로 곧바로 다시 잠긴다. */
        if (record.lockedUntil > 0) records.remove(keyOf(userId), record)
        return 0
    }

    fun recordFailure(userId: String) {
        records.compute(keyOf(userId)) { _, current ->
            val failures = (current?.failures ?: 0) + 1
            Record(failures, if (failures >= maxFailures) now() + lockMillis else 0)
        }
    }

    fun recordSuccess(userId: String) {
        records.remove(keyOf(userId))
    }

    private fun keyOf(userId: String): String = userId.trim().lowercase()

    private companion object {
        const val DEFAULT_MAX_FAILURES = 5
        const val DEFAULT_LOCK_MILLIS = 5 * 60 * 1000L
        const val MILLIS_PER_SECOND = 1000L
    }
}
