package com.eunilsung.talk.data.sample

import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.TestMyInfo
import com.eunilsung.talk.testsupport.createTestDatabase
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 대화방 목록 저장소 + SQLDelight 통합 — 방 조작과 그룹 칩 동기화를 확인한다. */
class LocalChatRoomListRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var settings: Settings

    @BeforeTest
    fun setUp() {
        TestMyInfo.loginAs()
        database = createTestDatabase()
        settings = MapSettings()
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    private fun newRepository() =
        LocalChatRoomListRepositoryImpl(FakeLoginRepository(), settings, ChatRoomMapper(), database)

    private suspend fun LocalChatRoomListRepositoryImpl.rooms(): List<ChatRoom.Item> =
        getChatRooms().first()

    private suspend fun LocalChatRoomListRepositoryImpl.groups(): List<ChatGroup> =
        getChatGroups().first()

    @Test
    fun 첫_조회에_대화방과_그룹_칩이_시드된다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()

        assertEquals(TestChatRooms.seed(TestMyInfo.ME).size, repo.rooms().size)
        assertEquals(listOf("cg_work", "cg_personal"), repo.groups().map { it.id })
    }

    @Test
    fun 고정된_방이_목록_맨_위에_온다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()

        assertEquals("room_group_dev", repo.rooms().first().id)   // 시드에서 고정된 방
    }

    @Test
    fun 상단고정은_켜고_끌_때마다_정렬이_바뀐다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()

        assertTrue(repo.setChatRoomPin("room_direct_3", true))
        assertEquals("room_direct_3", repo.rooms().first().id)   // 나중에 고정한 방이 더 위

        assertTrue(repo.setChatRoomPin("room_direct_3", false))
        assertEquals("room_group_dev", repo.rooms().first().id)
    }

    @Test
    fun 방_나가기는_행을_지우고_다시_읽어도_없다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()
        val before = repo.rooms().size

        assertTrue(repo.leaveChatRoom("room_direct_1"))
        assertEquals(before - 1, repo.rooms().size)
        assertNull(repo.rooms().firstOrNull { it.id == "room_direct_1" })

        assertFalse(repo.leaveChatRoom("room_direct_1"))   // 이미 없음

        val reopened = newRepository()
        reopened.fetchChatRooms()
        assertNull(reopened.rooms().firstOrNull { it.id == "room_direct_1" })
    }

    @Test
    fun 방을_나가면_그룹_칩에서도_빠진다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()
        assertContains(repo.groups().first { it.id == "cg_personal" }.roomIds, "room_direct_1")

        repo.leaveChatRoom("room_direct_1")

        val personal = repo.groups().first { it.id == "cg_personal" }
        assertFalse(personal.roomIds.contains("room_direct_1"))
    }

    @Test
    fun 방_이름_변경이_DB에_남는다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()

        assertTrue(repo.renameChatRoom("room_group_dev", "플랫폼 회의"))
        assertFalse(repo.renameChatRoom("room_group_dev", " "))
        assertFalse(repo.renameChatRoom("없는방", "아무거나"))

        val reopened = newRepository()
        reopened.fetchChatRooms()
        assertEquals("플랫폼 회의", reopened.rooms().first { it.id == "room_group_dev" }.title)
    }

    @Test
    fun 알림_설정이_DB에_남는다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()

        assertTrue(repo.setChatRoomAlarm("room_group_dev", "1"))
        assertFalse(repo.setChatRoomAlarm("없는방", "1"))

        val reopened = newRepository()
        reopened.fetchChatRooms()
        assertEquals("1", reopened.rooms().first { it.id == "room_group_dev" }.isAlarm)
    }

    @Test
    fun 그룹_칩은_생성_추가_제거_삭제가_된다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()

        repo.createChatGroup("임시")
        val created = repo.groups().first { it.name == "임시" }

        repo.addRoomToGroup(created.id, "room_direct_3")
        repo.addRoomToGroup(created.id, "room_direct_3")   // 중복 추가는 무시
        assertEquals(listOf("room_direct_3"), repo.groups().first { it.id == created.id }.roomIds)

        repo.removeRoomFromGroup(created.id, "room_direct_3")
        assertTrue(repo.groups().first { it.id == created.id }.roomIds.isEmpty())

        repo.deleteChatGroup(created.id)
        assertNull(repo.groups().firstOrNull { it.id == created.id })
    }

    @Test
    fun 그룹_칩_순서_변경이_sort에_반영된다() = runTest {
        val repo = newRepository()
        repo.fetchChatRooms()

        repo.reorderChatGroups(listOf("cg_personal", "cg_work"))

        assertEquals(listOf("cg_personal", "cg_work"), repo.groups().map { it.id })
        assertEquals(listOf("1", "2"), repo.groups().map { it.sort })
    }
}
