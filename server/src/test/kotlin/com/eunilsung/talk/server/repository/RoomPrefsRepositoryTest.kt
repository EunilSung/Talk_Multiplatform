package com.eunilsung.talk.server.repository

import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.ChatGroupDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 상단고정과 대화그룹 — 사람마다 따로 가지고, 남의 방이 끼어들지 않는지 본다. */
class RoomPrefsRepositoryTest {

    private lateinit var chats: ChatRepository
    private lateinit var groups: ChatGroupRepository
    private lateinit var roomA: String
    private lateinit var roomB: String

    @BeforeTest
    fun setUp() {
        TestDatabase.clean()
        SeedAccounts.ensure(UserRepository(TestDatabase.dataSource))
        chats = ChatRepository(TestDatabase.dataSource)
        groups = ChatGroupRepository(TestDatabase.dataSource)
        roomA = assertNotNull(chats.createRoom("test1", listOf("test2"))).roomId
        roomB = assertNotNull(chats.createRoom("test1", listOf("test2", "test3"))).roomId
    }

    @Test
    fun `고정은 나에게만 적용되고 풀면 사라진다`() {
        assertTrue(chats.setPinned(roomA, "test1", true))

        assertTrue(assertNotNull(chats.room(roomA, "test1")).pinnedAtEpochMillis > 0)
        assertEquals(0, chats.room(roomA, "test2")?.pinnedAtEpochMillis)

        assertTrue(chats.setPinned(roomA, "test1", false))
        assertEquals(0, chats.room(roomA, "test1")?.pinnedAtEpochMillis)
    }

    @Test
    fun `이미 고정한 방을 다시 고정해도 시각이 바뀌지 않고 방 밖의 사람은 고정할 수 없다`() {
        chats.setPinned(roomA, "test1", true)
        val first = chats.room(roomA, "test1")?.pinnedAtEpochMillis

        chats.setPinned(roomA, "test1", true)

        assertEquals(first, chats.room(roomA, "test1")?.pinnedAtEpochMillis)
        assertFalse(chats.setPinned(roomA, "test3", true))
    }

    @Test
    fun `그룹은 보낸 대로 통째로 바뀌고 순서대로 나온다`() {
        groups.replace("test1", listOf(ChatGroupDto("g2", "업무", 2, listOf(roomB)), ChatGroupDto("g1", "친구", 1, listOf(roomA, roomB))))
        groups.replace("test1", listOf(ChatGroupDto("g1", "가족", 1, listOf(roomA)), ChatGroupDto("g3", "새 그룹", 2)))

        val mine = groups.groups("test1")

        assertEquals(listOf("g1" to "가족", "g3" to "새 그룹"), mine.map { it.id to it.name })
        assertEquals(listOf(roomA), mine.first().roomIds)
        assertTrue(mine.last().roomIds.isEmpty())
    }

    @Test
    fun `그룹은 사람마다 따로이고 내가 없는 방은 담기지 않는다`() {
        val othersRoom = assertNotNull(chats.createRoom("test4", listOf("test5"))).roomId

        groups.replace("test1", listOf(ChatGroupDto("g1", "내 그룹", 1, listOf(roomA, othersRoom, "no-such-room"))))
        groups.replace("test2", listOf(ChatGroupDto("g1", "이서연의 그룹", 1, listOf(roomB))))

        assertEquals(listOf(roomA), groups.groups("test1").single().roomIds)
        assertEquals("이서연의 그룹", groups.groups("test2").single().name)
        assertTrue(groups.groups("test3").isEmpty())
    }

    @Test
    fun `방에서 나가면 그 방은 내 그룹과 고정에서 빠진다`() {
        chats.setPinned(roomB, "test1", true)
        groups.replace("test1", listOf(ChatGroupDto("g1", "업무", 1, listOf(roomA, roomB))))

        chats.leave(roomB, "test1")
        chats.invite(roomB, "test2", listOf("test1"))

        assertEquals(listOf(roomA), groups.groups("test1").single().roomIds)
        assertEquals(0, chats.room(roomB, "test1")?.pinnedAtEpochMillis)
    }
}
