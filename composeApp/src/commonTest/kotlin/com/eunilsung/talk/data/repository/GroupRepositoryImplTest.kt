package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.mapper.GroupMapper
import com.eunilsung.talk.data.remote.server.UserDirectory
import com.eunilsung.talk.data.repository.GroupRepositoryImpl.Companion.toGroupUser
import com.eunilsung.talk.data.sample.LocalGroupRepositoryImpl
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.MY_PROFILE_GROUP_ID
import com.eunilsung.talk.domain.repository.AddUserResult
import com.eunilsung.talk.shared.api.ContactGroupDto
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.eunilsung.talk.testsupport.TestMyInfo
import com.eunilsung.talk.testsupport.createTestDatabase
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 서버 내그룹 저장소 — 서버의 사람과 그룹이 화면 그룹으로 어떻게 짜이고, 고친 것이 서버에 어떻게 올라가는지 본다. */
class GroupRepositoryImplTest {

    private lateinit var server: FakeTalkServer
    private lateinit var directory: UserDirectory
    private lateinit var repo: GroupRepositoryImpl
    private lateinit var profiles: UserProfileRepositoryImpl

    @BeforeTest
    fun setUp() {
        TestMyInfo.loginAs()
        server = FakeTalkServer()
        directory = UserDirectory()
        val mapper = GroupMapper()
        val local = LocalGroupRepositoryImpl(
            mapper, FakeLoginRepository(), MapSettings(), createTestDatabase(),
            seedsSampleGroups = false,
            findUser = { userId, groupId -> directory.find(userId)?.toGroupUser(groupId) },
        )
        repo = GroupRepositoryImpl(local, server, directory, mapper)
        profiles = UserProfileRepositoryImpl(server, directory)
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    private suspend fun groups(): List<Group.Item> = repo.getGroups().first()

    private suspend fun membersOf(groupId: String): List<String> =
        groups().first { it.id == groupId }.userData.mapNotNull { it.userId }.sorted()

    @Test
    fun 그룹을_만들지_않아도_나를_뺀_모든_사람이_기본_그룹에_보인다() = runTest {
        repo.fetchGroups()

        assertEquals(setOf(MY_PROFILE_GROUP_ID, "0"), groups().map { it.id }.toSet())
        assertEquals(listOf("test2", "test3", "test5"), membersOf("0"))
        assertEquals("이서연", groups().first { it.id == "0" }.userData.first { it.userId == "test2" }.userName)
    }

    @Test
    fun 서버의_그룹에_든_사람은_기본_그룹에서_빠진다() = runTest {
        server.contactGroups = listOf(ContactGroupDto("1", "자주 연락", 1, listOf("test2", "test5")))

        repo.fetchGroups()

        assertEquals(listOf("test2", "test5"), membersOf("1"))
        assertEquals(listOf("test3"), membersOf("0"))
        assertEquals("자주 연락", groups().first { it.id == "1" }.name)
    }

    @Test
    fun 접속_중인_사람은_온라인으로_표시된다() = runTest {
        server.people = server.people.map { if (it.id == "test3") it.copy(isOnline = true) else it }

        repo.fetchGroups()

        val users = groups().first { it.id == "0" }.userData
        assertEquals("1", users.first { it.userId == "test3" }.mobileStatus)
        assertEquals("0", users.first { it.userId == "test2" }.mobileStatus)
    }

    @Test
    fun 그룹을_만들고_사람을_옮기면_서버에_올라가고_기본_그룹에서_빠진다() = runTest {
        repo.fetchGroups()

        assertTrue(repo.createGroup("프로젝트"))
        val groupId = groups().first { it.name == "프로젝트" }.id
        assertTrue(repo.moveUserToGroup("test2", "0", groupId))

        assertEquals(listOf("프로젝트" to listOf("test2")), server.contactGroups.map { it.name to it.memberIds })
        assertEquals(listOf("test2"), membersOf(groupId))
        assertEquals(listOf("test3", "test5"), membersOf("0"))
    }

    @Test
    fun 그룹에서_뺀_사람은_기본_그룹으로_돌아온다() = runTest {
        server.contactGroups = listOf(ContactGroupDto("1", "자주 연락", 1, listOf("test2", "test5")))
        repo.fetchGroups()

        assertTrue(repo.removeUserFromGroup("test2", "1"))

        assertEquals(listOf("test5"), membersOf("1"))
        assertEquals(listOf("test2", "test3"), membersOf("0"))
        assertEquals(listOf("test5"), server.contactGroups.single().memberIds)
    }

    @Test
    fun 이름을_바꾸고_지운_것도_서버에_반영된다() = runTest {
        server.contactGroups = listOf(ContactGroupDto("1", "옛 이름", 1, listOf("test2")), ContactGroupDto("2", "지울 그룹", 2))
        repo.fetchGroups()

        assertTrue(repo.renameGroup("1", "새 이름"))
        assertTrue(repo.deleteGroup("2"))

        assertEquals(listOf("새 이름"), server.contactGroups.map { it.name })
        assertEquals(listOf("test2"), server.contactGroups.single().memberIds)
    }

    @Test
    fun 서버가_받아_주지_않은_변경은_되돌린다() = runTest {
        server.contactGroups = listOf(ContactGroupDto("1", "자주 연락", 1, listOf("test2")))
        repo.fetchGroups()
        server.isReachable = false

        assertFalse(repo.createGroup("반영되면 안 되는 그룹"))
        assertEquals(AddUserResult.FAILED, repo.copyUserToGroup("test3", "1"))
        assertFalse(repo.renameGroup("1", "바뀌면 안 되는 이름"))

        assertEquals(listOf("자주 연락"), groups().filter { it.id == "1" || it.name.isNotBlank() && it.id != MY_PROFILE_GROUP_ID }.map { it.name })
        assertEquals(listOf("test2"), membersOf("1"))
    }

    @Test
    fun 서버에_닿지_못하면_받아_둔_그룹이_그대로_보인다() = runTest {
        server.contactGroups = listOf(ContactGroupDto("1", "자주 연락", 1, listOf("test2")))
        repo.fetchGroups()
        server.isReachable = false

        repo.fetchGroups()

        assertEquals(listOf("test2"), membersOf("1"))
        assertEquals(listOf("test3", "test5"), membersOf("0"))
    }

    @Test
    fun 프로필은_서버에서_받고_못_받으면_받아_둔_목록에서_찾는다() = runTest {
        repo.fetchGroups()

        val fresh = profiles.fetchProfile("test5").getOrThrow()
        assertEquals("정하윤", fresh.name)
        assertEquals("기획팀", fresh.department)
        assertEquals("차장", fresh.position)

        server.isReachable = false
        assertEquals("박도윤", profiles.fetchProfile("test3").getOrThrow().name)
        assertEquals("이서연", assertNotNull(profiles.getCachedProfile("test2")).name)
        assertTrue(profiles.fetchProfile("nobody").isFailure)
    }

    @Test
    fun 목록을_받는_사이에_만든_그룹은_늦게_도착한_옛_목록에_지워지지_않는다() = runTest {
        val listRead = CompletableDeferred<Unit>()
        val releaseList = CompletableDeferred<Unit>()
        server.afterGroupsRead = {
            server.afterGroupsRead = null
            listRead.complete(Unit)
            releaseList.await()
        }

        val fetching = launch(Dispatchers.Default) { repo.fetchGroups() }
        listRead.await()
        val creating = launch(Dispatchers.Default) { repo.createGroup("새 그룹") }
        withContext(Dispatchers.Default) { delay(INTERLEAVE_WAIT_MS) }
        releaseList.complete(Unit)
        fetching.join()
        creating.join()

        assertTrue(repo.getGroups().first().any { it.name == "새 그룹" })
        assertEquals(listOf("새 그룹"), server.contactGroups.map { it.name })
    }

    private companion object {
        /** 끼어든 일이 (막히지 않는다면) 끝나기에 충분한 시간. */
        const val INTERLEAVE_WAIT_MS = 300L
    }
}
