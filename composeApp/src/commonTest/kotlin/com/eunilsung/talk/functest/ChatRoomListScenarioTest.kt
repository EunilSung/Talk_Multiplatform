package com.eunilsung.talk.functest

import kotlinx.coroutines.flow.first
import kotlin.test.Test

/** 대화방 목록 — 만들기·이름·고정·알림·미리보기·안읽음·나가기가 서버를 거쳐 목록에 반영되는지. */
class ChatRoomListScenarioTest {

    @Test
    fun 대화방목록() = functionalSuite("대화방목록") { harness ->
        listOf(
            scenario("방만들기와설정") {
                val tag = runTag()
                var roomId = ""
                step("새 단체방이 목록에 나타난다") {
                    roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                    val room = harness.room(roomId)
                    require(room != null, "만든 방이 목록에 없다")
                    requireEquals(3, room!!.displayUserCount, "참여자 수")
                }
                step("이름을 바꾸면 목록의 이름이 바뀐다") {
                    require(harness.rooms.renameChatRoom(roomId, tag), "이름 바꾸기가 실패했다")
                    requireEquals(tag, harness.room(roomId)?.title, "방 이름")
                }
                step("고정하고 풀면 고정 표시가 따라간다") {
                    require(harness.rooms.setChatRoomPin(roomId, true), "고정이 실패했다")
                    require(harness.room(roomId)?.pinDate?.isNotBlank() == true, "고정했는데 고정 시각이 비어 있다")
                    require(harness.rooms.setChatRoomPin(roomId, false), "고정 해제가 실패했다")
                    requireEquals("", harness.room(roomId)?.pinDate, "고정 해제 뒤의 고정 시각")
                }
                step("알림을 끄고 켜면 알림 표시가 따라간다") {
                    val on = harness.room(roomId)?.isAlarm.orEmpty()
                    require(harness.rooms.setChatRoomAlarm(roomId, ALARM_OFF), "알림 끄기가 실패했다")
                    requireEquals(ALARM_OFF, harness.room(roomId)?.isAlarm, "끈 뒤의 알림 표시")
                    require(harness.rooms.setChatRoomAlarm(roomId, on), "알림 켜기가 실패했다")
                    requireEquals(on, harness.room(roomId)?.isAlarm, "다시 켠 뒤의 알림 표시")
                }
                step("나가면 목록에서 사라지고 서버에서도 내 방이 아니다") {
                    require(harness.rooms.leaveChatRoom(roomId), "나가기가 실패했다")
                    require(harness.room(roomId) == null, "나간 방이 목록에 남아 있다")
                    val mine = harness.server.rooms().must("방 목록").map { it.id }
                    require(roomId !in mine, "서버의 내 방 목록에 나간 방이 남아 있다")
                }
            },
            scenario("미리보기와안읽음") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                var roomId = ""
                step("상대가 보내면 알림만으로 미리보기와 안읽음이 바뀐다") {
                    roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                    peer.say(roomId, "$tag 첫 번째")
                    peer.say(roomId, "$tag 두 번째")
                    awaitTrue("미리보기가 상대의 마지막 말이 된다") {
                        harness.room(roomId)?.lastChatContent == "$tag 두 번째"
                    }
                    requireEquals("2", harness.room(roomId)?.unReadCount, "안읽음 수")
                }
                step("전체 안읽음 합계에 이 방의 몫이 들어 있다") {
                    val sum = harness.rooms.getChatRooms().first().sumOf { it.unReadCount.toIntOrNull() ?: 0 }
                    awaitTrue("합계가 목록의 합과 같아진다") { harness.rooms.unreadTotal.value == sum }
                    require(harness.rooms.unreadTotal.value >= 2, "합계가 이 방의 안읽음보다 작다")
                }
                step("다시 조회해도 서버의 값과 같다") {
                    harness.rooms.fetchChatRooms()
                    val server = harness.server.room(roomId).must("방 조회")
                    requireEquals(server.unreadCount.toString(), harness.room(roomId)?.unReadCount, "안읽음 수")
                }
            },
        )
    }

    private companion object {
        /** 화면이 쓰는 "알림 꺼짐" 값. 켜짐 값은 방에서 읽어 되돌린다. */
        const val ALARM_OFF = "1"
    }
}
