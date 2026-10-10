package com.eunilsung.talk.functest

import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteFormItem
import kotlinx.coroutines.flow.first
import kotlin.test.Test

/** 기본 흐름 밖의 갈래 — 대화의 다른 종류, 방의 다른 종류, 투표 옵션, AI 의 나머지 방식. */
class DetailScenarioTest {

    @Test
    fun 대화종류() = functionalSuite("대화종류") { harness ->
        listOf(
            scenario("이모티콘") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)

                step("이모티콘과 함께 보낸 말이 이모티콘 대화로 가고 상대에게도 그 이모티콘이 보인다") {
                    harness.chats.sendTextChat(roomId, "$tag 이모티콘과 함께", emoticonId = EMOTICON_ID)
                    awaitTrue("전송 완료") {
                        harness.chatsOf(roomId).any { it.emoticon.id == EMOTICON_ID && it.chatStatue == Chat.Statue.COMPLETE }
                    }
                    val mine = harness.chatsOf(roomId).first { it.emoticon.id == EMOTICON_ID }
                    requireEquals(Chat.Type.EMOTICON, mine.chatType, "대화 종류")
                    val seen = peer.messages(roomId).first { it.id == mine.chatID }
                    requireEquals(EMOTICON_ID, seen.payload?.emoticonId, "상대가 본 이모티콘")
                    require("$tag 이모티콘과 함께" in seen.content, "함께 보낸 말이 빠졌다: ${seen.content}")
                }
            },
            scenario("사진과회수") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)
                var fileId = ""
                var chatId = ""

                step("사진은 사진 대화로 가고 크기 정보가 상대에게 전해진다") {
                    harness.deviceFiles.put("/device/$tag.png", ByteArray(PHOTO_BYTES) { (it % 127).toByte() })
                    harness.chats.sendFile(roomId, "/device/$tag.png")
                    awaitTrue("사진 전송 완료") {
                        harness.chatsOf(roomId).any { it.originalFileName == "$tag.png" && it.chatStatue == Chat.Statue.COMPLETE }
                    }
                    val mine = harness.chatsOf(roomId).first { it.originalFileName == "$tag.png" }
                    chatId = mine.chatID
                    requireEquals(Chat.Type.IMAGE, mine.chatType, "대화 종류")
                    val seen = peer.messages(roomId).first { it.id == chatId }
                    require(!seen.payload?.imageSize.isNullOrBlank(), "사진 크기 정보가 전해지지 않았다")
                    fileId = seen.payload?.fileId.orEmpty()
                    peer.client.downloadFile(fileId).must("회수 전 내려받기")
                }
                step("사진 대화를 회수하면 그 파일도 더는 받을 수 없다") {
                    harness.chats.recallChat(roomId, chatId)
                    awaitTrue("회수 반영") { harness.chatsOf(roomId).first { it.chatID == chatId }.isRecalled }
                    val after = peer.client.downloadFile(fileId)
                    require(after !is ServerResult.Success, "회수한 대화의 파일이 아직 내려받아진다")
                }
            },
        )
    }

    @Test
    fun 방종류() = functionalSuite("방종류") { harness ->
        listOf(
            /**
             * 둘만의 방과 나와의 대화방은 계정에 하나뿐이라 사람이 쓰던 방일 수 있다.
             * 여기서는 방을 찾기만 하고 대화를 보내지도, 나가지도 않는다.
             */
            scenario("둘만의방") {
                step("같은 상대와는 방이 새로 생기지 않고 있던 방이 돌아온다") {
                    val first = harness.invite.inviteUsers("", listOf(RealServerHarness.PEER to "상대"), existingUserCount = 1)
                    val second = harness.invite.inviteUsers("", listOf(RealServerHarness.PEER to "상대"), existingUserCount = 1)
                    require(!first.isNullOrBlank(), "둘만의 방을 얻지 못했다")
                    requireEquals(first, second, "두 번째로 얻은 방")
                    requireEquals(true, harness.room(first!!)?.isDirectRoom, "둘만의 방 표시")
                }
            },
            scenario("나와의대화") {
                step("나만 있는 방도 하나만 생기고 다시 청하면 그 방이 돌아온다") {
                    val first = harness.server.createRoom(listOf(RealServerHarness.ME)).must("나와의 대화방").id
                    val second = harness.server.createRoom(listOf(RealServerHarness.ME)).must("나와의 대화방").id
                    requireEquals(first, second, "두 번째로 얻은 방")
                    val members = harness.server.room(first).must("방 조회").members.filterNot { it.hasLeft }.map { it.id }
                    requireEquals(listOf(RealServerHarness.ME), members, "참여자")
                }
            },
            scenario("대화그룹순서") {
                val tag = runTag()

                suspend fun mine() = harness.rooms.getChatGroups().first().filter { it.name.startsWith(tag) }.map { it.name }
                suspend fun onServer() = harness.server.chatGroups().must("대화그룹 조회")
                    .filter { it.name.startsWith(tag) }.sortedBy { it.sort }.map { it.name }

                step("대화그룹의 순서를 바꾸면 서버에도 그 순서로 남는다") {
                    harness.rooms.fetchChatRooms()
                    harness.rooms.createChatGroup("$tag-가")
                    harness.rooms.createChatGroup("$tag-나")
                    requireEquals(listOf("$tag-가", "$tag-나"), onServer(), "만든 순서")
                    val all = harness.rooms.getChatGroups().first()
                    val swapped = all.filterNot { it.name.startsWith(tag) }.map { it.id } +
                        all.filter { it.name.startsWith(tag) }.map { it.id }.reversed()
                    harness.rooms.reorderChatGroups(swapped)
                    requireEquals(listOf("$tag-나", "$tag-가"), onServer(), "바꾼 뒤 서버의 순서")
                    requireEquals(listOf("$tag-나", "$tag-가"), mine(), "바꾼 뒤 목록의 순서")
                }
            },
        )
    }

    @Test
    fun 투표옵션() = functionalSuite("투표옵션") { harness ->
        listOf(
            scenario("여러개고르기와다시투표") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)
                var voteId = ""

                step("여러 개를 고를 수 있는 투표에서는 고른 만큼 표가 들어간다") {
                    val form = VoteForm(
                        title = "$tag 간식",
                        items = listOf(VoteFormItem(0, "과자"), VoteFormItem(1, "과일"), VoteFormItem(2, "음료")),
                        multiSelect = true,
                    )
                    voteId = harness.votes.createVote(roomId, form).getOrNull().orEmpty()
                    require(voteId.isNotBlank(), "투표가 만들어지지 않았다")
                    val mine = harness.votes.submitVote(roomId, voteId, setOf(0, 2))
                    requireEquals(listOf(1, 0, 1), mine?.items?.sortedBy { it.idx }?.map { it.nVote }, "항목별 득표")
                    requireEquals(true, mine?.multiSelect, "여러 개 고르기 표시")
                }
                step("하나만 고르는 투표에 여러 개를 보내면 서버가 받지 않는다") {
                    val single = harness.votes.createVote(
                        roomId, VoteForm(title = "$tag 하나만", items = listOf(VoteFormItem(0, "가"), VoteFormItem(1, "나"))),
                    ).getOrNull().orEmpty()
                    val result = peer.client.castVote(roomId, single, listOf(0, 1))
                    require(result !is ServerResult.Success, "하나만 고르는 투표가 두 표를 받았다")
                }
                step("다시 투표하면 내 표가 빠지고 참여자에서도 빠진다") {
                    val cleared = harness.votes.reVote(roomId, voteId)
                    requireEquals(listOf(0, 0, 0), cleared?.items?.sortedBy { it.idx }?.map { it.nVote }, "표를 뺀 뒤의 득표")
                    requireEquals(0, cleared?.participantCount, "참여자 수")
                }
            },
        )
    }

    @Test
    fun AI방식() = functionalSuite("AI방식") { harness ->
        listOf(
            scenario("다듬기의다른방식") {
                val draft = "혹시 시간 괜찮으시면 내일 오후쯤에 잠깐 회의 가능할지 여쭤봅니다"
                listOf(PolishStyle.POLITE to "polite", PolishStyle.CONCISE to "concise").forEach { (style, code) ->
                    step("$code 방식으로 다듬은 글이 돌아온다") {
                        val polished = aiResult({ harness.server.polish(draft, code) }) {
                            harness.chats.polishText(draft, style)
                        }
                        require(!polished.isNullOrBlank(), "다듬은 글이 비어 있다")
                    }
                }
            },
            scenario("이미그언어인글의번역") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val message = peer.say(roomId, "내일 오후 세 시에 회의실에서 만나요")
                harness.chats.fetchChats(roomId)

                step("한국어 글을 한국어로 청하면 다른 언어로 옮겨져 온다") {
                    val translation = aiResult({ harness.server.translate(roomId, message.id, "ko") }) {
                        harness.chats.translateChat(roomId, message.id, "ko")
                    }
                    require(!translation.isNullOrBlank(), "번역이 비어 있다")
                    require(translation != message.content, "원문이 그대로 돌아왔다 ($tag)")
                }
                step("회수된 대화와 모르는 언어는 번역되지 않는다") {
                    val recalled = peer.say(roomId, "거둘 말")
                    peer.client.recallMessage(roomId, recalled.id).must("상대 회수")
                    val afterRecall = harness.server.translate(roomId, recalled.id, "ko")
                    require(afterRecall is ServerResult.Rejected && afterRecall.status == 404, "회수된 대화가 번역됐다: $afterRecall")
                    val badLanguage = harness.server.translate(roomId, message.id, "zz")
                    require(badLanguage is ServerResult.Rejected && badLanguage.status == 400, "모르는 언어를 받았다: $badLanguage")
                }
            },
        )
    }

    private companion object {
        /** 서버는 이모티콘 id 를 그대로 싣고 갈 뿐이라, 왕복하는지만 본다. */
        const val EMOTICON_ID = "ft-emoticon"
        const val PHOTO_BYTES = 8 * 1024
    }
}
