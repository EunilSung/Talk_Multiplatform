package com.eunilsung.talk.server.local

import com.eunilsung.talk.server.limits.ServerLimits
import kotlin.test.Test
import kotlin.test.assertEquals

/** 로컬 서버는 따로 정하지 않으면 이 PC 안(127.0.0.1)에서만 받고, 그때만 넉넉한 한도를 쓴다. */
class LocalServerTest {

    @Test
    fun `HOST 가 없으면 루프백에만 열고 주면 그 주소를 쓴다`() {
        assertEquals("127.0.0.1", localServerHost(null))
        assertEquals("127.0.0.1", localServerHost("  "))
        assertEquals("0.0.0.0", localServerHost("0.0.0.0"))
    }

    @Test
    fun `넉넉한 한도는 루프백에만 열 때 쓰고 같은 망에 열면 운영 한도로 돌아간다`() {
        assertEquals(LOCAL_LIMITS, localServerLimits("127.0.0.1"))
        assertEquals(LOCAL_LIMITS, localServerLimits("localhost"))
        assertEquals(ServerLimits(), localServerLimits("0.0.0.0"))
        assertEquals(ServerLimits(), localServerLimits("192.168.0.10"))
    }
}
