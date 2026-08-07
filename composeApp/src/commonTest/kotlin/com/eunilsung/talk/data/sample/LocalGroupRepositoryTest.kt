package com.eunilsung.talk.data.sample

import com.eunilsung.talk.data.mapper.GroupMapper
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.repository.AddUserResult
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

/** 그룹 저장소 + SQLDelight 통합 — 변경이 DB 를 거쳐 다시 조회되는지 확인한다. */
class LocalGroupRepositoryTest {

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
        LocalGroupRepositoryImpl(GroupMapper(), FakeLoginRepository(), settings, database)

    private suspend fun LocalGroupRepositoryImpl.groups(): List<Group.Item> = getGroups().first()

    private fun List<Group.Item>.memberIdsOf(groupId: String): List<String> =
        first { it.id == groupId }.userData.map { it.userId.orEmpty() }

    @Test
    fun 첫_조회에_기본_그룹이_시드된다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        val ids = repo.groups().map { it.id }
        assertEquals(setOf("myprofile", TestGroups.DEFAULT_GROUP_ID, "1", "2", "3"), ids.toSet())
        assertEquals("myprofile", ids.first())   // 내 프로필은 항상 최상단
    }

    @Test
    fun 그룹_멤버에서_나는_빠진다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        // 개발팀 시드는 test1~test4 지만 로그인한 test1 은 제외되어야 한다.
        val devMembers = repo.groups().memberIdsOf("1")
        assertEquals(listOf("test2", "test3", "test4"), devMembers.sorted())
    }

    @Test
    fun 이미_시드된_DB는_다시_시드하지_않는다() = runTest {
        newRepository().fetchGroups()

        val second = newRepository()
        second.fetchGroups()
        assertEquals(5, second.groups().size)
    }

    @Test
    fun 그룹_생성은_겹치지_않는_id를_받는다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        assertTrue(repo.createGroup("신규팀"))

        val created = repo.groups().first { it.name == "신규팀" }
        assertEquals("4", created.id)   // 기존 최대 숫자 id(3) 다음
        assertEquals(6, repo.groups().size)
    }

    @Test
    fun 빈_이름으로는_그룹을_만들_수_없다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        assertFalse(repo.createGroup("  "))
        assertEquals(5, repo.groups().size)
    }

    @Test
    fun 사용자_추가는_중복과_없는_그룹을_구분한다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        assertEquals(AddUserResult.SUCCESS, repo.copyUserToGroup("test5", "1"))
        assertContains(repo.groups().memberIdsOf("1"), "test5")

        assertEquals(AddUserResult.ALREADY_EXISTS, repo.copyUserToGroup("test5", "1"))
        assertEquals(AddUserResult.FAILED, repo.copyUserToGroup("test5", "없는그룹"))
        assertEquals(AddUserResult.FAILED, repo.copyUserToGroup("없는사용자", "1"))
    }

    @Test
    fun 사용자_삭제는_해당_그룹에서만_빠진다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()
        repo.copyUserToGroup("test2", "2")

        assertTrue(repo.removeUserFromGroup("test2", "1"))
        assertFalse(repo.groups().memberIdsOf("1").contains("test2"))
        assertContains(repo.groups().memberIdsOf("2"), "test2")

        assertFalse(repo.removeUserFromGroup("test2", "1"))   // 이미 없음
    }

    @Test
    fun 사용자_이동은_원본_그룹에서_지운다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        assertTrue(repo.moveUserToGroup("test2", fromGroupId = "1", toGroupId = "2"))
        assertFalse(repo.groups().memberIdsOf("1").contains("test2"))
        assertContains(repo.groups().memberIdsOf("2"), "test2")

        assertFalse(repo.moveUserToGroup("test2", fromGroupId = "2", toGroupId = "2"))
    }

    @Test
    fun 그룹_이름_변경이_DB에_남는다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        assertTrue(repo.renameGroup("1", "플랫폼팀"))
        assertFalse(repo.renameGroup("1", " "))
        assertFalse(repo.renameGroup("없는그룹", "아무거나"))

        // 새 인스턴스로 다시 읽어도 유지 = DB 에 반영됐다는 뜻.
        val reopened = newRepository()
        reopened.fetchGroups()
        assertEquals("플랫폼팀", reopened.groups().first { it.id == "1" }.name)
    }

    @Test
    fun 그룹_삭제는_소속_사용자까지_지운다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        assertTrue(repo.deleteGroup("1"))
        assertNull(repo.groups().firstOrNull { it.id == "1" })

        val reopened = newRepository()
        reopened.fetchGroups()
        assertEquals(4, reopened.groups().size)
        assertNull(reopened.groups().firstOrNull { it.id == "1" })
    }

    @Test
    fun 펼침_상태는_DB가_아니라_Settings에_남는다() = runTest {
        val repo = newRepository()
        repo.fetchGroups()

        repo.toggleGroupExpanded("1", true)
        assertTrue(repo.groups().first { it.id == "1" }.isExpanded)

        val reopened = newRepository()
        reopened.fetchGroups()
        assertTrue(reopened.groups().first { it.id == "1" }.isExpanded)
    }
}
