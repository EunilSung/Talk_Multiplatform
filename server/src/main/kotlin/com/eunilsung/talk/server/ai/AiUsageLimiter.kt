package com.eunilsung.talk.server.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * AI 호출 횟수 제한.
 *
 * 두 겹으로 막는다. 한 사람이 한 시간에 부를 수 있는 횟수와, 서버 전체가 하루에 부를 수 있는 횟수다.
 * 앞쪽은 한 사람이 혼자 다 써 버리는 것을, 뒤쪽은 제공처의 무료 한도를 넘겨 모두가 못 쓰게 되는 것을 막는다.
 *
 * 기록은 메모리에만 둔다. 서버를 다시 띄우면 지워지지만, 진짜 상한은 제공처가 쥐고 있어
 * 여기서 틀려도 넘치지는 않는다.
 */
class AiUsageLimiter(
    private val perUserHourly: Int = DEFAULT_PER_USER_HOURLY,
    private val globalDaily: Int = DEFAULT_GLOBAL_DAILY,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class Window(val startedAt: Long, val count: Int)

    private val byUser = ConcurrentHashMap<String, Window>()
    private var global = Window(0, 0)

    /** 한 번 쓸 수 있으면 횟수를 올리고 true. 한도에 걸렸으면 아무것도 올리지 않고 false. */
    @Synchronized
    fun tryAcquire(userId: String): Boolean {
        val time = now()
        val day = global.takeIf { time - it.startedAt < DAY_MILLIS } ?: Window(time, 0)
        val hour = byUser[userId]?.takeIf { time - it.startedAt < HOUR_MILLIS } ?: Window(time, 0)
        if (day.count >= globalDaily || hour.count >= perUserHourly) return false
        global = day.copy(count = day.count + 1)
        byUser[userId] = hour.copy(count = hour.count + 1)
        return true
    }

    private companion object {
        const val DEFAULT_PER_USER_HOURLY = 20
        /** 무료 등급의 하루 한도보다 낮게 잡는다. 정확한 한도는 계정마다 달라 넉넉히 남긴다. */
        const val DEFAULT_GLOBAL_DAILY = 300
        const val HOUR_MILLIS = 60 * 60 * 1000L
        const val DAY_MILLIS = 24 * HOUR_MILLIS
    }
}
