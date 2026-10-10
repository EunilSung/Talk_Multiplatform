package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.Chat
import kotlin.test.Test

/** 읽음 — 내가 읽으면 방의 안읽음이, 상대가 읽으면 말풍선의 안읽음 수가 내려가는지. */
class ReadScenarioTest {

    @Test
    fun 읽음() = functionalSuite("읽음") { harness ->
        listOf(
            scenario("내가읽기") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)

                step("읽으면 방의 안읽음이 0 이 되고 서버도 그렇게 안다") {
                    val last = peer.say(roomId, "$tag 읽어 주세요")
                    harness.chats.fetchChats(roomId)
                    awaitTrue("안읽음이 쌓인다") { (harness.room(roomId)?.unReadCount?.toIntOrNull() ?: 0) >= 1 }
                    require(harness.chats.markChatAsRead(roomId, last.id), "읽음 처리가 실패했다")
                    harness.rooms.fetchChatRooms()
                    requireEquals("0", harness.room(roomId)?.unReadCount, "읽은 뒤 방의 안읽음")
                    requireEquals(0, harness.server.room(roomId).must("방 조회").unreadCount, "서버가 아는 안읽음")
                }
                step("상대가 본 그 대화의 안읽음 수가 내가 읽은 만큼 줄었다") {
                    val seen = peer.messages(roomId).first { it.content == "$tag 읽어 주세요" }
                    requireEquals(1, seen.unreadCount, "세 명 방에서 한 명만 안 읽은 상태")
                }
            },
            scenario("상대가읽기") {
                val peer = harness.peer(RealServerHarness.PEER)
                val third = harness.peer(RealServerHarness.THIRD)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)

                suspend fun myChat() = harness.chatsOf(roomId).first { it.chatContent == "$tag 내 말" }

                step("보낸 직후의 말풍선에는 안 읽은 사람 수가 붙는다") {
                    harness.chats.sendTextChat(roomId, "$tag 내 말")
                    awaitTrue("전송 완료") {
                        harness.chatsOf(roomId).any { it.chatContent == "$tag 내 말" && it.chatStatue == Chat.Statue.COMPLETE }
                    }
                    requireEquals("2", myChat().unReadCount, "보낸 직후의 안읽음 수")
                }
                step("상대가 한 명씩 읽을 때마다 알림으로 수가 내려간다") {
                    val id = myChat().chatID
                    peer.read(roomId, id)
                    awaitTrue("한 명 읽음") { myChat().unReadCount == "1" }
                    third.read(roomId, id)
                    awaitTrue("모두 읽음") { myChat().unReadCount == "0" }
                }
                step("다시 물어도 서버의 수와 같다") {
                    harness.chats.refreshChatUnreadCounts(roomId)
                    requireEquals("0", myChat().unReadCount, "다시 조회한 안읽음 수")
                }
            },
        )
    }
}
