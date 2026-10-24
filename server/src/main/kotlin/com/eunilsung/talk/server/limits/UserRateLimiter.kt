package com.eunilsung.talk.server.limits

import java.util.concurrent.ConcurrentHashMap

/**
 * 사람마다 1분·하루 횟수 제한.
 *
 * 방 만들기·초대처럼 한 번에 다른 사람을 끌어들이는 요청에 건다. 한 사람이 다른 사람을 방 수천 개에 넣어
 * 그 사람의 목록과 서버를 막지 못하게 한다.
 *
 * 기록은 메모리에만 둔다. 서버를 다시 띄우면 지워지지만, 다시 띄우는 동안 막히는 것으로 충분하다.
 */
class UserRateLimiter(
    private val perMinute: Int,
    private val perDay: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class Window(val startedAt: Long, val count: Int)

    private val minutes = ConcurrentHashMap<String, Window>()
    private val days = ConcurrentHashMap<String, Window>()

    /** 한 번 할 수 있으면 횟수를 올리고 true. 한도에 걸렸으면 아무것도 올리지 않고 false. */
    @Synchronized
    fun tryAcquire(userId: String): Boolean {
        val time = now()
        val minute = minutes[userId]?.takeIf { time - it.startedAt < MINUTE_MILLIS } ?: Window(time, 0)
        val day = days[userId]?.takeIf { time - it.startedAt < DAY_MILLIS } ?: Window(time, 0)
        if (minute.count >= perMinute || day.count >= perDay) return false
        minutes[userId] = minute.copy(count = minute.count + 1)
        days[userId] = day.copy(count = day.count + 1)
        return true
    }

    private companion object {
        const val MINUTE_MILLIS = 60 * 1000L
        const val DAY_MILLIS = 24 * 60 * MINUTE_MILLIS
    }
}
