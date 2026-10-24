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
 *
 * 기록이 끝없이 쌓이지 않게 한다. 마지막 실패에서 [failureWindowMillis] 가 지나고 잠겨 있지 않은 기록은 없는 것으로
 * 보고 지운다. 기록이 [maxRecords] 개에 닿으면 그런 기록부터, 그래도 넘치면 잠기지 않은 것 가운데 가장 오래 조용했던 기록부터 버린다 —
 * 없는 아이디를 바꿔 가며 넣어도 메모리가 차지 않는다.
 */
class LoginAttemptLimiter(
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
    private val lockMillis: Long = DEFAULT_LOCK_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
    private val failureWindowMillis: Long = DEFAULT_FAILURE_WINDOW_MILLIS,
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
) {

    private data class Record(val failures: Int, val lockedUntil: Long, val lastFailureAt: Long)

    private val records = ConcurrentHashMap<String, Record>()

    /** 지금 잠겨 있으면 풀릴 때까지 남은 초, 아니면 0. */
    fun secondsUntilUnlocked(userId: String): Long {
        val key = keyOf(userId)
        val record = records[key] ?: return 0
        val time = now()
        val remaining = record.lockedUntil - time
        if (remaining > 0) return (remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        /** 잠금이 끝났으면 기록을 지운다. 남겨 두면 다음 한 번의 실수로 곧바로 다시 잠긴다. */
        if (record.lockedUntil > 0 || record.isStale(time)) records.remove(key, record)
        return 0
    }

    fun recordFailure(userId: String) {
        val key = keyOf(userId)
        if (records.size >= maxRecords && !records.containsKey(key)) prune()
        val time = now()
        records.compute(key) { _, current ->
            val failures = (current?.takeUnless { it.isStale(time) }?.failures ?: 0) + 1
            Record(failures, if (failures >= maxFailures) time + lockMillis else 0, time)
        }
    }

    fun recordSuccess(userId: String) {
        records.remove(keyOf(userId))
    }

    /**
     * 오래된 기록부터 버려 [maxRecords] 아래로 내린다. 잠긴 기록은 마지막에 버린다 — 기록을 넘치게 해 남의 잠금을 풀지 못하게 한다.
     */
    private fun prune() {
        val time = now()
        records.entries.removeIf { it.value.isStale(time) }
        while (records.size >= maxRecords) {
            val oldest = records.entries.filter { it.value.lockedUntil <= time }.minByOrNull { it.value.lastFailureAt }
                ?: records.entries.minByOrNull { it.value.lastFailureAt }
                ?: break
            records.remove(oldest.key, oldest.value)
        }
    }

    /** 잠겨 있지 않고 마지막 실패에서 [failureWindowMillis] 가 지났다. */
    private fun Record.isStale(time: Long): Boolean = lockedUntil <= time && time - lastFailureAt >= failureWindowMillis

    private fun keyOf(userId: String): String = userId.trim().lowercase()

    private companion object {
        const val DEFAULT_MAX_FAILURES = 5
        const val DEFAULT_LOCK_MILLIS = 5 * 60 * 1000L
        const val DEFAULT_FAILURE_WINDOW_MILLIS = 15 * 60 * 1000L
        const val DEFAULT_MAX_RECORDS = 10_000
        const val MILLIS_PER_SECOND = 1000L
    }
}
