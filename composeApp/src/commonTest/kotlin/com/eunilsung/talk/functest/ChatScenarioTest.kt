package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.Chat
import kotlin.test.Test

/** 대화 — 보내기·받기·순서·답장·멘션·과거 대화·검색이 서버를 거쳐 화면이 보는 목록에 반영되는지. */
class ChatScenarioTest {

    @Test
    fun 대화() = functionalSuite("대화") { harness ->
        listOf(
            scenario("주고받기") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)

                step("내가 보낸 대화는 전송 완료가 되고 상대에게 보인다") {
                    harness.chats.sendTextChat(roomId, "$tag 내가 보냄")
                    awaitTrue("전송 완료") {
                        harness.chatsOf(roomId).any { it.chatContent == "$tag 내가 보냄" && it.chatStatue == Chat.Statue.COMPLETE }
                    }
                    val seen = peer.messages(roomId).filter { it.content == "$tag 내가 보냄" }
                    requireEquals(1, seen.size, "상대가 본 내 대화의 수")
                    requireEquals(RealServerHarness.ME, seen.single().senderId, "보낸 사람")
                }
                step("상대가 보낸 대화가 알림만으로 목록에 들어온다") {
                    peer.say(roomId, "$tag 상대가 보냄")
                    awaitTrue("상대 대화 도착") { harness.chatsOf(roomId).any { it.chatContent == "$tag 상대가 보냄" } }
                    val arrived = harness.chatsOf(roomId).first { it.chatContent == "$tag 상대가 보냄" }
                    requireEquals(peer.id, arrived.user.id, "보낸 사람")
                    requireEquals(peer.name, arrived.user.name, "보낸 사람 이름")
                    require(!arrived.isMe, "상대의 대화가 내 것으로 보인다")
                }
                step("번갈아 보내도 서버가 매긴 순서대로 쌓인다") {
                    peer.say(roomId, "$tag 순서 1")
                    harness.chats.sendTextChat(roomId, "$tag 순서 2")
                    awaitTrue("내 대화 완료") {
                        harness.chatsOf(roomId).any { it.chatContent == "$tag 순서 2" && it.chatStatue == Chat.Statue.COMPLETE }
                    }
                    peer.say(roomId, "$tag 순서 3")
                    awaitTrue("세 대화가 모두 도착") { harness.chatsOf(roomId).count { "$tag 순서" in it.chatContent } == 3 }
                    val mine = harness.chatsOf(roomId).map { it.chatContent }.filter { "$tag 순서" in it }
                    val theirs = peer.messages(roomId).map { it.content }.filter { "$tag 순서" in it }
                    requireEquals(listOf("$tag 순서 1", "$tag 순서 2", "$tag 순서 3"), mine, "내 목록의 순서")
                    requireEquals(mine, theirs, "상대가 본 순서")
                }
            },
            scenario("답장과멘션") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)

                step("답장은 원래 대화를 달고 가고 상대에게도 그렇게 보인다") {
                    val origin = peer.say(roomId, "$tag 원래 말")
                    awaitTrue("원래 대화 도착") { harness.chatsOf(roomId).any { it.chatID == origin.id } }
                    val target = harness.chatsOf(roomId).first { it.chatID == origin.id }
                    harness.chats.sendTextChat(roomId, "$tag 답장", replyTarget = target)
                    awaitTrue("답장 전송 완료") {
                        harness.chatsOf(roomId).any { it.chatContent == "$tag 답장" && it.chatStatue == Chat.Statue.COMPLETE }
                    }
                    val reply = harness.chatsOf(roomId).first { it.chatContent == "$tag 답장" }
                    requireEquals(Chat.Type.REPLY, reply.chatType, "대화 종류")
                    requireEquals(origin.id, reply.replyChat.chatID, "답장이 가리키는 대화")
                    requireEquals("$tag 원래 말", reply.replyChat.chatContent, "인용된 내용")
                    val seen = peer.messages(roomId).first { it.content == "$tag 답장" }
                    requireEquals(origin.id, seen.payload?.replyToId, "상대가 본 답장 대상")
                }
                step("나를 멘션하면 목록의 멘션 수가 오른다") {
                    val before = harness.room(roomId)?.mentionCount?.toIntOrNull() ?: 0
                    peer.say(roomId, "<mention>@${com.eunilsung.talk.Config.MyInfo.userName}</mention> $tag 확인 부탁")
                    awaitTrue("멘션 수 증가") {
                        (harness.room(roomId)?.mentionCount?.toIntOrNull() ?: 0) == before + 1
                    }
                }
            },
            scenario("과거대화와검색") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)

                step("한 쪽을 넘는 대화는 최근 것부터 받고 올리면 나머지가 온다") {
                    repeat(OLD_MESSAGES) { peer.say(roomId, "$tag 쌓인 말 ${it + 1}") }
                    harness.chats.fetchChats(roomId)
                    val firstPage = harness.chatsOf(roomId).count { tag in it.chatContent }
                    require(firstPage in 1 until OLD_MESSAGES, "첫 쪽이 전부이거나 비었다: $firstPage")
                    require(harness.chats.fetchMoreChats(roomId), "더 오래된 대화를 받지 못했다")
                    awaitTrue("전부 도착") { harness.chatsOf(roomId).count { tag in it.chatContent } == OLD_MESSAGES }
                    val numbers = harness.chatsOf(roomId).filter { tag in it.chatContent }
                        .map { it.chatContent.substringAfterLast(' ').toInt() }
                    requireEquals((1..OLD_MESSAGES).toList(), numbers, "오래된 것부터의 순서")
                }
                step("받아 둔 대화에서 낱말로 찾는다") {
                    val hits = harness.chats.searchChats(roomId, "쌓인 말 7", userId = "", dateFrom = "", dateTo = "")
                    val contents = harness.chatsOf(roomId).filter { chat -> hits.any { it.first == chat.chatID } }
                        .map { it.chatContent }
                    require("$tag 쌓인 말 7" in contents, "찾는 대화가 결과에 없다: $contents")
                }
            },
            scenario("참여자") {
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                step("방의 참여자가 서버의 참여자와 같다") {
                    val users = harness.chats.fetchChatRoomUsers(roomId).map { it.id }.toSet()
                    requireEquals(setOf(RealServerHarness.ME, RealServerHarness.PEER, RealServerHarness.THIRD), users, "참여자")
                }
            },
        )
    }

    private companion object {
        /** 한 쪽(100개)을 넘기는 수. */
        const val OLD_MESSAGES = 120
    }
}
