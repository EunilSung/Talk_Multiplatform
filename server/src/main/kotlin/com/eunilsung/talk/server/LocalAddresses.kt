package com.eunilsung.talk.server

import java.net.DatagramSocket
import java.net.InetAddress

/**
 * 이 PC 가 같은 망에서 어떤 주소로 보이는지.
 *
 * 앱은 서버 주소를 빌드할 때 박아 넣는다(`local.properties` 의 `server.baseUrl`). PC 주소가 바뀌면
 * 앱은 없는 주소로 접속하다 타임아웃만 낸다. 뜰 때 주소를 찍어 두면 바로 대조할 수 있다.
 */
object LocalAddresses {

    /**
     * 밖으로 나갈 때 실제로 쓰는 주소.
     *
     * 어댑터 목록에는 가상 머신이 만든 사설 주소도 섞여 있어 고르기 어렵다. 바깥으로 향하는 소켓이
     * 어느 주소를 고르는지 물어 하나로 좁힌다. UDP 는 연결해도 패킷이 나가지 않는다.
     */
    fun primaryAddress(): String? = runCatching {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 53)
            socket.localAddress.hostAddress?.takeIf { it != "0.0.0.0" }
        }
    }.getOrNull()

    /** 로그 한 줄. */
    fun describe(port: Int): String {
        val device = primaryAddress()?.let { "http://$it:$port" } ?: "사용할 수 있는 주소 없음(Wi-Fi 확인)"
        return "접속 주소 — 에뮬레이터=http://10.0.2.2:$port · iOS 시뮬레이터=http://localhost:$port · 실기기=$device"
    }
}
