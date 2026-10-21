package com.eunilsung.talk.functest

import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import kotlin.test.Test

/** 대화 전달 — 받은 글·사진을 다른 방에 보내면 그 방 사람에게 새 대화로 보이고, 첨부는 그 방 사람만 받는지. */
class ForwardScenarioTest {

    @Test
    fun 전달() = functionalSuite("전달") { harness ->
        listOf(
            scenario("글과사진") {
                val peer = harness.peer(RealServerHarness.PEER)
                val outsider = harness.peer(OUTSIDER)
                val tag = runTag()
                val sourceRoomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val targetRoomId = harness.newRoom(OUTSIDER, RealServerHarness.THIRD)
                harness.chats.fetchChats(sourceRoomId)
                harness.chats.fetchChats(targetRoomId)

                step("받은 글을 다른 방에 전달하면 그 방 사람에게 멘션 태그 없이 내 글로 보인다") {
                    peer.client.sendMessage(
                        sourceRoomId,
                        SendMessageRequest("ft-fw-${nowMillis()}", "<mention>@김민준</mention> $tag 회의록"),
                    ).must("상대 글")
                    awaitTrue("상대 글 도착") { harness.chatsOf(sourceRoomId).any { it.chatContent.contains("$tag 회의록") } }
                    val source = harness.chatsOf(sourceRoomId).first { it.chatContent.contains("$tag 회의록") }

                    harness.chats.forwardChat(targetRoomId, sourceRoomId, source)

                    awaitTrue("전달 완료") {
                        harness.chatsOf(targetRoomId).any { it.chatContent == "@김민준 $tag 회의록" && it.chatStatus == Chat.Status.COMPLETE }
                    }
                    val seen = outsider.messages(targetRoomId).first { it.content.contains("$tag 회의록") }
                    requireEquals(MessageKind.TEXT, seen.kind, "받는 쪽 대화 종류")
                    requireEquals("@김민준 $tag 회의록", seen.content, "받는 쪽 본문")
                    requireEquals(RealServerHarness.ME, seen.senderId, "보낸 사람")
                }
                step("받은 사진을 전달하면 받을 방에만 있는 새 파일로 간다") {
                    val bytes = ByteArray(PHOTO_BYTES) { (it % 251).toByte() }
                    val file = peer.client.uploadFile(sourceRoomId, "$tag.jpg", bytes).must("상대 올리기")
                    peer.client.sendMessage(
                        sourceRoomId,
                        SendMessageRequest(
                            "ft-fw-photo-${nowMillis()}", "", MessageKind.IMAGE,
                            MessagePayloadDto(fileId = file.id, fileName = file.name, fileSize = file.size, imageSize = "40:30"),
                        ),
                    ).must("상대 사진 대화")
                    awaitTrue("사진 내려받기") {
                        harness.chatsOf(sourceRoomId).any { it.originalFileName == "$tag.jpg" && it.imagePath.isNotBlank() }
                    }
                    val source = harness.chatsOf(sourceRoomId).first { it.originalFileName == "$tag.jpg" }

                    harness.chats.forwardChat(targetRoomId, sourceRoomId, source)

                    awaitTrue("사진 전달 완료") {
                        harness.chatsOf(targetRoomId).any {
                            it.originalFileName == "$tag.jpg" && it.isMe && it.chatStatus == Chat.Status.COMPLETE
                        }
                    }
                    val seen = outsider.messages(targetRoomId).first { it.payload?.fileName == "$tag.jpg" }
                    requireEquals(MessageKind.IMAGE, seen.kind, "받는 쪽 대화 종류")
                    requireEquals("40:30", seen.payload?.imageSize, "사진 크기")
                    val copiedId = seen.payload?.fileId.orEmpty()
                    require(copiedId.isNotBlank() && copiedId != file.id, "원본 파일 id 가 그대로 실렸다")
                    val downloaded = outsider.client.downloadFile(copiedId).must("받는 쪽 내려받기")
                    require(downloaded.contentEquals(bytes), "복사된 파일 내용이 원본과 다르다")
                    require(outsider.client.downloadFile(file.id) !is ServerResult.Success, "다른 방 사람이 원본 파일을 받았다")
                }
            },
        )
    }

    private companion object {
        const val OUTSIDER = "test5"
        const val PHOTO_BYTES = 16 * 1024
    }
}
