package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.Chat
import kotlin.test.Test

/** 공감·회수·공지·책갈피 — 내가 한 것은 서버가 받아 준 대로, 상대가 한 것은 알림으로 반영되는지. */
class ChatExtrasScenarioTest {

    @Test
    fun 공감회수공지책갈피() = functionalSuite("공감회수공지책갈피") { harness ->
        listOf(
            scenario("공감") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)
                val target = peer.say(roomId, "$tag 공감할 말")
                awaitTrue("대화 도착") { harness.chatsOf(roomId).any { it.chatID == target.id } }

                suspend fun reactors() = harness.chatsOf(roomId).first { it.chatID == target.id }.empathy.empathy2.map { it.id }

                step("내가 누른 공감이 말풍선에 붙고 상대에게도 보인다") {
                    harness.chats.sendEmpathy(roomId, target.id, EMPATHY)
                    awaitTrue("내 공감 반영") { RealServerHarness.ME in reactors() }
                    val seen = peer.messages(roomId).first { it.id == target.id }.reactions
                    require(seen.any { it.userId == RealServerHarness.ME && it.kind == EMPATHY }, "상대에게 내 공감이 안 보인다: $seen")
                }
                step("상대가 누른 공감이 알림으로 붙는다") {
                    peer.client.toggleReaction(roomId, target.id, EMPATHY).must("상대 공감")
                    awaitTrue("상대 공감 반영") { peer.id in reactors() }
                }
                step("다시 누르면 내 공감만 빠진다") {
                    harness.chats.sendEmpathy(roomId, target.id, EMPATHY)
                    awaitTrue("내 공감 해제") { RealServerHarness.ME !in reactors() }
                    requireEquals(listOf(peer.id), reactors(), "남은 공감")
                }
            },
            scenario("회수") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)

                step("내 대화를 회수하면 본문이 비고 상대에게도 회수된 것으로 보인다") {
                    harness.chats.sendTextChat(roomId, "$tag 거둘 말")
                    awaitTrue("전송 완료") {
                        harness.chatsOf(roomId).any { it.chatContent == "$tag 거둘 말" && it.chatStatue == Chat.Statue.COMPLETE }
                    }
                    val mine = harness.chatsOf(roomId).first { it.chatContent == "$tag 거둘 말" }
                    harness.chats.recallChat(roomId, mine.chatID)
                    awaitTrue("회수 반영") { harness.chatsOf(roomId).first { it.chatID == mine.chatID }.isRecalled }
                    val seen = peer.messages(roomId).first { it.id == mine.chatID }
                    require(seen.isRecalled, "상대에게 회수된 것으로 보이지 않는다")
                    require("거둘 말" !in seen.content, "회수한 본문이 상대에게 남아 있다")
                }
                step("상대가 회수하면 알림으로 내 목록의 그 대화가 회수된다") {
                    val theirs = peer.say(roomId, "$tag 상대가 거둘 말")
                    awaitTrue("대화 도착") { harness.chatsOf(roomId).any { it.chatID == theirs.id } }
                    peer.client.recallMessage(roomId, theirs.id).must("상대 회수")
                    awaitTrue("상대 회수 반영") { harness.chatsOf(roomId).first { it.chatID == theirs.id }.isRecalled }
                }
                step("남의 대화는 회수되지 않는다") {
                    val theirs = peer.say(roomId, "$tag 남의 말")
                    awaitTrue("대화 도착") { harness.chatsOf(roomId).any { it.chatID == theirs.id } }
                    harness.chats.recallChat(roomId, theirs.id)
                    require(!peer.messages(roomId).first { it.id == theirs.id }.isRecalled, "남의 대화가 회수됐다")
                }
            },
            scenario("공지") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)

                step("공지를 올리면 공지바에 걸리고 상대에게도 같은 공지가 보인다") {
                    harness.chats.addNotice(roomId, "$tag 공지입니다")
                    awaitTrue("공지 반영") { harness.chats.currentNotice.value?.content == "$tag 공지입니다" }
                    requireEquals("$tag 공지입니다", peer.client.notice(roomId).must("상대 공지 조회").content, "상대가 본 공지")
                }
                step("상대가 공지를 바꾸면 알림으로 공지바가 바뀐다") {
                    peer.client.setNotice(roomId, "$tag 바뀐 공지").must("상대 공지 등록")
                    awaitTrue("바뀐 공지 반영") { harness.chats.currentNotice.value?.content == "$tag 바뀐 공지" }
                    requireEquals(peer.id, harness.chats.currentNotice.value?.ownerId, "공지 올린 사람")
                }
                step("공지를 내리면 공지바가 비고 서버에도 없다") {
                    harness.chats.deleteNotice(roomId)
                    awaitTrue("공지 내림") { harness.chats.currentNotice.value == null }
                    requireEquals("", peer.client.notice(roomId).must("상대 공지 조회").content, "내린 뒤 상대가 본 공지")
                }
            },
            scenario("책갈피") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val message = peer.say(roomId, "$tag 남길 말")
                harness.chats.fetchChats(roomId)

                step("꽂은 책갈피가 목록에 보이고 서버에 남는다") {
                    harness.chats.addBookmark(roomId, harness.chatsOf(roomId).first { it.chatID == message.id })
                    awaitTrue("책갈피 반영") { harness.chats.bookmarks.value.any { it.chatId == message.id } }
                    val saved = harness.server.bookmarks(roomId).must("책갈피 조회").map { it.messageId }
                    requireEquals(listOf(message.id), saved, "서버의 책갈피")
                }
                step("책갈피는 내 것이라 상대에게는 없다") {
                    require(peer.client.bookmarks(roomId).must("상대 책갈피 조회").isEmpty(), "상대에게 내 책갈피가 보인다")
                }
                step("빼면 목록과 서버에서 사라진다") {
                    harness.chats.deleteBookmark(roomId, message.id)
                    awaitTrue("책갈피 해제") { harness.chats.bookmarks.value.none { it.chatId == message.id } }
                    require(harness.server.bookmarks(roomId).must("책갈피 조회").isEmpty(), "서버에 책갈피가 남아 있다")
                }
            },
        )
    }

    private companion object {
        /** 공감 여섯 가지 중 하나. 앱과 서버가 같은 번호를 쓴다. */
        const val EMPATHY = "2"
    }
}
