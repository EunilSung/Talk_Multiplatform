package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.data.local.LocalSecret
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.ReplyChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.testsupport.TestLocalSecret
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 저장하는 길목인 매퍼가 **본문과 사람 정보를 잠가서 DB 에 넣고, 꺼낼 때 푸는지.**
 *
 * 이 기능을 켜기 전에 저장된 평문 행도 그대로 읽혀야 한다 — 접두어가 없으면 손대지 않는다.
 */
class MapperSealingTest {

    @BeforeTest
    fun setUp() = TestLocalSecret.install()

    @Test
    fun 대화_본문과_제목은_잠겨_저장되고_읽을_때_풀린다() {
        val chat = Chat.Item(chatID = "c1", title = "회의 3시", chatContent = "회의 3시에 시작합니다")

        val entity = ChatMapper().toEntity("test1", "room1", chat)

        assertTrue(LocalSecret.isSealed(entity.chatContent.orEmpty()))
        assertTrue(LocalSecret.isSealed(entity.title.orEmpty()))
        assertFalse(entity.chatContent.orEmpty().contains("회의"))
        assertEquals("회의 3시에 시작합니다", ChatMapper().toModel(entity).chatContent)
        assertEquals("회의 3시", ChatMapper().toModel(entity).title)
    }

    @Test
    fun 답장_인용문은_답장일_때만_잠기고_일반_대화는_그대로_둔다() {
        val reply = Chat.Item(
            chatID = "c2",
            chatType = Chat.Type.REPLY,
            chatContent = "알겠습니다",
            replyChat = ReplyChat(chatID = "c1", chatContent = "인용되는 원본 글", user = User(id = "u", name = "홍길동")),
        )
        val plainChat = Chat.Item(chatID = "c3", chatContent = "그냥 대화")

        val replyEntity = ChatMapper().toEntity("test1", "room1", reply)
        val plainEntity = ChatMapper().toEntity("test1", "room1", plainChat)

        assertTrue(LocalSecret.isSealed(replyEntity.replyChatJson))
        assertFalse(replyEntity.replyChatJson.contains("인용되는"))
        assertEquals("인용되는 원본 글", ChatMapper().toModel(replyEntity).replyChat.chatContent)
        assertFalse(LocalSecret.isSealed(plainEntity.replyChatJson), "답장이 아닌 대화의 빈 인용문까지 잠갔다")
    }

    @Test
    fun 이_기능을_켜기_전에_저장된_평문_행도_읽힌다() {
        val sealed = ChatMapper().toEntity("test1", "room1", Chat.Item(chatID = "c1", chatContent = "본문"))
        val legacy = sealed.copy(chatContent = "예전에 저장된 평문 본문", title = "예전 제목")

        val model = ChatMapper().toModel(legacy)

        assertEquals("예전에 저장된 평문 본문", model.chatContent)
        assertEquals("예전 제목", model.title)
    }

    @Test
    fun 대화방_이름_미리보기_참여자_목록은_잠겨_저장된다() {
        val room = ChatRoom.Item(
            id = "room1",
            title = "개발팀 회의",
            lastChatContent = "빌드 통과했습니다",
            totalUserList = "test1-김민준|test2-이서연",
            exitUserList = "test3-박도윤",
            clsUsr = "test3",
        )

        val entity = ChatRoomMapper().toEntity("test1", room)

        listOf(entity.title, entity.lastChatContent, entity.totalUserList, entity.exitUserList, entity.clsUsr).forEach {
            assertTrue(LocalSecret.isSealed(it.orEmpty()), "잠기지 않은 칸이 있다: $it")
        }
        assertEquals(room.copy(), ChatRoomMapper().toModel(entity))
    }

    @Test
    fun 그룹_사용자의_이름_연락처_부서는_잠겨_저장된다() {
        val user = Group.User(
            groupId = "g1",
            userId = "test2",
            userName = "이서연",
            phoneNumber = "010-1000-0002",
            email = "test2@example.com",
            departmentName = "개발1팀",
            positionName = "대리",
        )

        val entity = GroupMapper().toEntity("test1", user)

        assertTrue(LocalSecret.isSealed(entity.userName))
        assertTrue(LocalSecret.isSealed(entity.phoneNumber.orEmpty()))
        assertEquals("test2", entity.userId, "조회에 쓰는 아이디까지 잠갔다")
        val back = GroupMapper().toGroupUser(entity)
        assertEquals("이서연", back.userName)
        assertEquals("010-1000-0002", back.phoneNumber)
        assertEquals("test2@example.com", back.email)
        assertEquals("개발1팀", back.departmentName)
    }

    @Test
    fun 보낸_사람의_이름과_소속은_잠기고_아이디는_평문이다() {
        val chat = Chat.Item(
            chatID = "c9",
            chatContent = "본문",
            user = User(id = "test2", name = "이서연", departmentName = "개발1팀", positionName = "대리"),
        )

        val entity = ChatMapper().toEntity("test1", "room1", chat)

        assertTrue(entity.userJson.contains("\"id\":\"test2\""), "조회에 쓰는 아이디까지 잠갔다")
        assertFalse(entity.userJson.contains("이서연"))
        assertFalse(entity.userJson.contains("개발1팀"))
        val user = ChatMapper().toModel(entity).user
        assertEquals("이서연", user.name)
        assertEquals("개발1팀", user.departmentName)
        assertEquals("대리", user.positionName)
    }
}
