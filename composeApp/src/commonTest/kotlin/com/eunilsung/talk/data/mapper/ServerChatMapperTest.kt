package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.testsupport.FakeFileMetadataResolver
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.eunilsung.talk.testsupport.TestMyInfo
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 서버 대화 → 화면 대화 — 보낸 사람이 정해 보낸 payload 칸 중 화면에 그대로 그리면 안 되는 것을 걸러 내는지.
 *
 * 답장 인용은 답장 대화일 때만 붙이고, 파일 이름은 경로를 떼어 마지막 조각만 남긴다.
 */
class ServerChatMapperTest {

    private val mapper = ServerChatMapper(ServerFileStore(MapSettings(), FakeTalkServer(), FakeFileMetadataResolver()))

    private val forgedQuote = MessagePayloadDto(
        replyToId = "origin-1",
        replyKind = MessageKind.TEXT,
        replyAuthorId = "test9",
        replyAuthorName = "임채원",
        replyText = "하지 않은 말",
    )

    @BeforeTest
    fun setUp() = TestMyInfo.loginAs()

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    private fun message(kind: String, payload: MessagePayloadDto?) = MessageDto(
        roomId = "room-1", seq = 1, id = "m-1", senderId = "test2", senderName = "이서연",
        kind = kind, content = "네 처리하겠습니다", payload = payload,
    )

    @Test
    fun 답장_대화는_인용을_붙인다() = runTest {
        val chat = mapper.toChat(message(MessageKind.REPLY, forgedQuote))

        assertEquals(Chat.Type.REPLY, chat.chatType)
        assertEquals("origin-1", chat.replyChat.chatID)
        assertEquals("하지 않은 말", chat.replyChat.chatContent)
    }

    @Test
    fun 답장이_아닌_대화에_실린_인용_칸은_버린다() = runTest {
        listOf(MessageKind.TEXT, MessageKind.EMOTICON, MessageKind.FILE).forEach { kind ->
            val chat = mapper.toChat(message(kind, forgedQuote))

            assertEquals("", chat.replyChat.chatID, kind)
            assertEquals("", chat.replyChat.chatContent, kind)
            assertEquals("", chat.replyChat.user.name, kind)
        }
    }

    @Test
    fun 파일_이름의_경로는_떼고_마지막_조각만_남긴다() = runTest {
        val chat = mapper.toChat(
            message(MessageKind.FILE, MessagePayloadDto(fileId = "f-1", fileName = "../../Library/Preferences/x.plist", fileSize = 3))
        )

        assertEquals("x.plist", chat.originalFileName)
    }

    @Test
    fun 파일_이름이_없으면_빈_값이다() = runTest {
        val chat = mapper.toChat(message(MessageKind.FILE, MessagePayloadDto(fileId = "f-1", fileSize = 3)))

        assertEquals("", chat.originalFileName)
    }
}
