package com.eunilsung.talk.server.limits

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * 한 사람이 서버 자원을 혼자 다 쓰지 못하게 거는 한도들.
 *
 * 운영 기본값은 공개 테스트 서버(1GB 인스턴스) 기준이다. 테스트는 작은 값을 넣어 한도에 걸리는지 보고,
 * 로컬 개발 서버는 기능 테스트·실화면 흐름이 짧은 시간에 방과 파일을 많이 만들어 넉넉한 값을 쓴다.
 */
data class ServerLimits(
    /**
     * JSON 요청 본문의 최대 바이트. 파일 올리기는 [com.eunilsung.talk.shared.api.MAX_FILE_BYTES] 를 따로 본다.
     * 가장 큰 JSON 은 대화그룹 통째 바꾸기(방 id 를 모두 싣는다)라 그만큼은 넉넉히 둔다.
     */
    val jsonBodyBytes: Long = DEFAULT_JSON_BODY_BYTES,
    /** 한 사람이 하루(최근 24시간)에 올리거나 전달로 복사할 수 있는 파일 수. */
    val fileUploadsPerDay: Int = 200,
    /** 한 사람이 하루(최근 24시간)에 올리거나 전달로 복사할 수 있는 파일의 총 바이트. */
    val fileBytesPerDay: Long = 500L * MEGABYTE,
    /**
     * 한 사람이 동시에 올리고 있을 수 있는 파일 수. 올리는 중인 파일은 하루 한도에 아직 잡히지 않아, 이 상한이 없으면
     * 동시에 연 올리기 수만큼 디스크와 연결을 잡아 둘 수 있다. 앱은 한 기기에서 파일을 하나씩 올린다 — 두 기기와 다시 보내기가
     * 겹쳐도 걸리지 않게 3 이다.
     */
    val concurrentUploadsPerUser: Int = 3,
    /** 파일 하나를 다 받기까지 기다리는 최대 시간. 넘으면 끊는다 — 아주 느리게 보내는 올리기가 자리를 계속 잡지 못하게 한다. */
    val uploadTimeout: Duration = 10.minutes,
    /** 한 사람이 1분·하루에 새로 만들 수 있는 단체방 수. 이미 있는 1:1 방을 여는 것은 세지 않는다. */
    val roomCreatesPerMinute: Int = 30,
    val roomCreatesPerDay: Int = 300,
    /** 한 사람이 1분·하루에 보낼 수 있는 초대 요청 수. */
    val invitesPerMinute: Int = 30,
    val invitesPerDay: Int = 500,
    /**
     * 한 사람이 1분·하루에 만들 수 있는 투표 수. 투표 목록(`GET /rooms/{id}/votes`)은 방의 투표를 모두 읽으므로, 개수에 상한이
     * 없으면 투표를 수천 개 만들어 그 방을 여는 요청마다 서버 메모리를 채울 수 있다.
     */
    val voteCreatesPerMinute: Int = 30,
    val voteCreatesPerDay: Int = 300,
    /** 방 하나에 함께 있을 수 있는 사람 수. */
    val maxRoomMembers: Int = 300,
    /** 한 사람이 동시에 열어 둘 수 있는 알림 연결 수. 넘으면 가장 오래된 연결을 닫는다. */
    val webSocketsPerUser: Int = 5,
    /** 한 사람에게 묶어 둘 수 있는 푸시 토큰 수. 넘으면 가장 오래 갱신되지 않은 것을 지운다. */
    val pushTokensPerUser: Int = 10,
) {
    private companion object {
        const val MEGABYTE = 1024L * 1024
        const val DEFAULT_JSON_BODY_BYTES = 256L * 1024
    }
}
