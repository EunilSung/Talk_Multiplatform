package com.eunilsung.talk.server.limits

/**
 * 사람마다 동시에 하고 있을 수 있는 일의 수.
 *
 * 파일 올리기처럼 한 건이 오래 걸리고 그동안 연결·디스크를 잡는 요청에 건다. 끝나면 [release] 로 자리를 돌려준다.
 * 기록은 메모리에만 둔다 — 서버를 다시 띄우면 진행 중인 요청도 함께 끊긴다.
 */
class UserConcurrencyLimiter(private val maxPerUser: Int) {

    private val running = HashMap<String, Int>()

    /** 자리가 있으면 하나 잡고 true. 이미 [maxPerUser] 개를 하고 있으면 false. */
    @Synchronized
    fun tryAcquire(userId: String): Boolean {
        val count = running[userId] ?: 0
        if (count >= maxPerUser) return false
        running[userId] = count + 1
        return true
    }

    @Synchronized
    fun release(userId: String) {
        val count = running[userId] ?: return
        if (count <= 1) running.remove(userId) else running[userId] = count - 1
    }
}
