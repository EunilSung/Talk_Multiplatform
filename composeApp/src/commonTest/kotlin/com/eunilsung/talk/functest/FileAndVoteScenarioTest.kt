package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteFormItem
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import kotlin.test.Test

/** 파일과 투표 — 올린 파일이 그대로 내려오는지, 투표의 생성·참여·마감이 양쪽에 같은 결과로 보이는지. */
class FileAndVoteScenarioTest {

    @Test
    fun 파일() = functionalSuite("파일") { harness ->
        listOf(
            scenario("올리고받기") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)

                step("내가 보낸 파일을 상대가 같은 내용으로 내려받는다") {
                    val bytes = "기능 테스트 파일 $tag".encodeToByteArray()
                    harness.deviceFiles.put("/device/$tag.txt", bytes)
                    harness.chats.sendFile(roomId, "/device/$tag.txt")
                    awaitTrue("파일 전송 완료") {
                        harness.chatsOf(roomId).any { it.originalFileName == "$tag.txt" && it.chatStatue == Chat.Statue.COMPLETE }
                    }
                    requireEquals(Chat.Type.FILE, harness.chatsOf(roomId).first { it.originalFileName == "$tag.txt" }.chatType, "대화 종류")
                    val seen = peer.messages(roomId).first { it.payload?.fileName == "$tag.txt" }
                    requireEquals(bytes.size.toLong(), seen.payload?.fileSize, "상대가 본 파일 크기")
                    val downloaded = peer.client.downloadFile(seen.payload?.fileId.orEmpty()).must("상대 내려받기")
                    require(downloaded.contentEquals(bytes), "내려받은 내용이 올린 것과 다르다")
                }
                step("상대가 올린 파일이 알림으로 목록에 들어오고 내려받아진다") {
                    val bytes = ByteArray(FILE_BYTES) { (it % 251).toByte() }
                    val file = peer.client.uploadFile(roomId, "$tag-상대.bin", bytes).must("상대 올리기")
                    peer.client.sendMessage(
                        roomId,
                        SendMessageRequest(
                            "ft-file-${nowMillis()}", "", MessageKind.FILE,
                            MessagePayloadDto(fileId = file.id, fileName = file.name, fileSize = file.size),
                        ),
                    ).must("상대 파일 대화")
                    awaitTrue("파일 대화 도착") { harness.chatsOf(roomId).any { it.originalFileName == "$tag-상대.bin" } }
                    val downloaded = harness.server.downloadFile(file.id).must("내려받기")
                    require(downloaded.contentEquals(bytes), "내려받은 내용이 상대가 올린 것과 다르다")
                }
                step("방 밖의 사람은 그 파일을 받지 못한다") {
                    val outsider = harness.peer(OUTSIDER)
                    val fileId = peer.messages(roomId).first { it.payload?.fileName == "$tag.txt" }.payload?.fileId.orEmpty()
                    val result = outsider.client.downloadFile(fileId)
                    require(result !is com.eunilsung.talk.data.remote.server.ServerResult.Success, "방 밖의 사람이 파일을 받았다")
                }
            },
        )
    }

    @Test
    fun 투표() = functionalSuite("투표") { harness ->
        listOf(
            scenario("만들고참여하고마감") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.chats.fetchChats(roomId)
                var voteId = ""

                step("투표를 만들면 투표 대화가 생기고 상대의 투표 목록에 보인다") {
                    val form = VoteForm(title = "$tag 점심", items = listOf(VoteFormItem(0, "짜장"), VoteFormItem(1, "짬뽕")))
                    voteId = harness.votes.createVote(roomId, form).getOrNull().orEmpty()
                    require(voteId.isNotBlank(), "투표가 만들어지지 않았다")
                    awaitTrue("투표 대화 도착") { harness.chatsOf(roomId).any { it.chatType == Chat.Type.VOTE && it.vote?.id == voteId } }
                    require(peer.client.votes(roomId).must("상대 투표 목록").any { it.id == voteId }, "상대 목록에 투표가 없다")
                }
                step("상대와 내가 표를 던지면 득표가 그대로 집계된다") {
                    peer.client.castVote(roomId, voteId, listOf(0)).must("상대 투표")
                    val mine = harness.votes.submitVote(roomId, voteId, setOf(1))
                    require(mine != null, "내 투표가 받아들여지지 않았다")
                    val fresh = harness.votes.fetchVote(roomId, voteId)
                    requireEquals(2, fresh?.participantCount, "참여자 수")
                    requireEquals(listOf(1, 1), fresh?.items?.sortedBy { it.idx }?.map { it.nVote }, "항목별 득표")
                    requireEquals(1, fresh?.myItemIdx(RealServerHarness.ME), "내가 고른 항목")
                }
                step("목록에는 내가 만든 투표이고 참여한 것으로 나온다") {
                    val summary = harness.votes.fetchVotes(roomId).first { it.id == voteId }
                    require(summary.isMine && summary.hasVoted && !summary.isClosed, "목록의 표시가 다르다: $summary")
                }
                step("마감하면 마감 대화가 생기고 더는 표를 받지 않는다") {
                    harness.votes.closeVote(roomId, voteId, "$tag 점심")
                    awaitTrue("마감 대화 도착") { harness.chatsOf(roomId).any { it.chatType == Chat.Type.VOTE_COMPLETE } }
                    requireEquals(true, harness.votes.fetchVote(roomId, voteId)?.isClosed, "마감 여부")
                    val late = harness.peer(RealServerHarness.THIRD).client.castVote(roomId, voteId, listOf(0))
                    require(late !is com.eunilsung.talk.data.remote.server.ServerResult.Success, "마감한 투표가 표를 받았다")
                }
            },
        )
    }

    private companion object {
        const val OUTSIDER = "test5"
        const val FILE_BYTES = 64 * 1024
    }
}
