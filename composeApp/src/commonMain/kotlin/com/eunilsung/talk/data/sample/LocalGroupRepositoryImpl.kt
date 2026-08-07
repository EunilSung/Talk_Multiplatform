package com.eunilsung.talk.data.sample

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.GroupMapper
import com.eunilsung.talk.data.repository.SettingsKeys
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.repository.AddUserResult
import com.eunilsung.talk.domain.repository.GroupRepository
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.util.Log

/** [TestAccounts]·[TestGroups] 를 원본으로 쓰고 저장은 SQLDelight 로 하는 그룹 저장소. */
class LocalGroupRepositoryImpl(
    private val mapper: GroupMapper,
    private val loginRepository: LoginRepository,
    private val settings: Settings,
    database: AppDatabase,
) : GroupRepository {

    private val dbQueries = database.appDatabaseQueries
    private val _groups = MutableStateFlow<List<Group.Item>>(emptyList())
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        repositoryScope.launch {
            loginRepository.isLoggedIn.filter { it }.collect { seedIfNeeded() }
        }
        repositoryScope.launch {
            loginRepository.isLoggedIn.drop(1).filter { !it }.collect {
                // DB 는 그대로 두고 화면 상태만 비운다.
                Log.message("[Group/Local] clear view on logout")
                _groups.value = emptyList()
            }
        }
    }

    override fun getGroups(): Flow<List<Group.Item>> = _groups.asStateFlow()

    override suspend fun fetchGroups() = seedIfNeeded()

    /** 펼침 상태는 DB 가 아닌 [Settings] 소관. */
    override fun toggleGroupExpanded(groupId: String, isExpanded: Boolean) {
        settings.putBoolean(SettingsKeys.KEY_GROUP_EXPANDED_PREFIX + groupId, isExpanded)
        _groups.update { groups ->
            groups.map { if (it.id == groupId) it.copy(isExpanded = isExpanded) else it }
        }
    }

    override suspend fun copyUserToGroup(userId: String, targetGroupId: String): AddUserResult =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            val account = TestAccounts.find(userId) ?: return@withContext AddUserResult.FAILED
            val groupExists = dbQueries.selectGroupsByMyId(myId).executeAsList()
                .any { it.groupId == targetGroupId }
            if (!groupExists) return@withContext AddUserResult.FAILED

            val already = dbQueries.selectUsersByGroupId(myId, targetGroupId).executeAsList()
                .any { it.userId == userId }
            if (already) return@withContext AddUserResult.ALREADY_EXISTS

            dbQueries.insertGroupUser(mapper.toEntity(myId, account.toGroupUser(targetGroupId)))
            refreshFromDb(myId)
            Log.message("[Group/Local] copyUserToGroup $userId → $targetGroupId")
            AddUserResult.SUCCESS
        }

    override suspend fun removeUserFromGroup(userId: String, targetGroupId: String): Boolean =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            val existed = dbQueries.selectUsersByGroupId(myId, targetGroupId).executeAsList()
                .any { it.userId == userId }
            if (!existed) return@withContext false

            dbQueries.deleteGroupUser(myId = myId, groupId = targetGroupId, userId = userId)
            refreshFromDb(myId)
            Log.message("[Group/Local] removeUserFromGroup $userId ← $targetGroupId")
            true
        }

    override suspend fun moveUserToGroup(
        userId: String,
        fromGroupId: String,
        toGroupId: String,
    ): Boolean = withContext(Dispatchers.Default) {
        if (fromGroupId == toGroupId) return@withContext false
        val myId = Config.MyInfo.userId
        val account = TestAccounts.find(userId) ?: return@withContext false
        val targetExists = dbQueries.selectGroupsByMyId(myId).executeAsList()
            .any { it.groupId == toGroupId }
        if (!targetExists) return@withContext false

        dbQueries.transaction {
            dbQueries.deleteGroupUser(myId = myId, groupId = fromGroupId, userId = userId)
            dbQueries.insertGroupUser(mapper.toEntity(myId, account.toGroupUser(toGroupId)))
        }
        refreshFromDb(myId)
        Log.message("[Group/Local] moveUserToGroup $userId: $fromGroupId → $toGroupId")
        true
    }

    override suspend fun renameGroup(groupId: String, newName: String): Boolean =
        withContext(Dispatchers.Default) {
            if (newName.isBlank()) return@withContext false
            val myId = Config.MyInfo.userId
            val exists = dbQueries.selectGroupsByMyId(myId).executeAsList().any { it.groupId == groupId }
            if (!exists) return@withContext false

            dbQueries.updateGroupName(name = newName, myId = myId, groupId = groupId)
            refreshFromDb(myId)
            Log.message("[Group/Local] renameGroup $groupId → '$newName'")
            true
        }

    override suspend fun deleteGroup(groupId: String): Boolean = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        val exists = dbQueries.selectGroupsByMyId(myId).executeAsList().any { it.groupId == groupId }
        if (!exists) return@withContext false

        dbQueries.transaction {
            dbQueries.deleteGroupUsersByGroupId(myId = myId, groupId = groupId)
            dbQueries.deleteGroupById(myId = myId, groupId = groupId)
        }
        settings.remove(SettingsKeys.KEY_GROUP_EXPANDED_PREFIX + groupId)
        refreshFromDb(myId)
        Log.message("[Group/Local] deleteGroup $groupId")
        true
    }

    override suspend fun createGroup(name: String): Boolean = withContext(Dispatchers.Default) {
        if (name.isBlank()) return@withContext false
        val myId = Config.MyInfo.userId
        if (myId.isBlank()) return@withContext false

        // 기존 그룹 id 와 겹치지 않도록 DB 의 최대 숫자 id 다음 값을 쓴다.
        val nextId = ((dbQueries.selectGroupsByMyId(myId).executeAsList()
            .mapNotNull { it.groupId.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()

        dbQueries.insertGroup(
            mapper.toEntity(
                myId,
                Group.Item(id = nextId, name = name, alineCode = nextId, userData = emptyList())
            )
        )
        refreshFromDb(myId)
        Log.message("[Group/Local] createGroup '$name' (id=$nextId)")
        true
    }

    // ---- 내부 ----

    /** 해당 계정의 그룹이 DB 에 없을 때만 초기 배치를 넣는다. */
    private suspend fun seedIfNeeded() = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        if (myId.isBlank()) return@withContext

        if (dbQueries.selectGroupsByMyId(myId).executeAsList().isEmpty()) {
            val seeded = buildSeed(myId)
            dbQueries.transaction {
                seeded.forEach { group ->
                    dbQueries.insertGroup(mapper.toEntity(myId, group))
                    group.userData.forEach { user ->
                        dbQueries.insertGroupUser(mapper.toEntity(myId, user))
                    }
                }
            }
            Log.message("[Group/Local] seeded into DB — ${seeded.size} groups (me=$myId)")
        }
        refreshFromDb(myId)
    }

    /** 초기 배치 — 내 프로필 / 기본그룹 / 사용자 그룹. */
    private fun buildSeed(myId: String): List<Group.Item> {
        val myProfile = mapper.toMyProfileGroup().let { group ->
            group.copy(userData = listOf(mapper.toMyProfileUser().copy(groupId = group.id)))
        }
        val defaultGroup = Group.Item(
            id = TestGroups.DEFAULT_GROUP_ID,
            name = "",
            alineCode = TestGroups.DEFAULT_GROUP_ALINE_CODE,
            userData = usersOf(TestGroups.DEFAULT_GROUP_MEMBER_IDS, TestGroups.DEFAULT_GROUP_ID, myId),
        )
        val custom = TestGroups.CUSTOM.map { seed ->
            Group.Item(
                id = seed.id,
                name = seed.name,
                alineCode = seed.alineCode,
                userData = usersOf(seed.memberIds, seed.id, myId),
            )
        }
        return listOf(myProfile, defaultGroup) + custom
    }

    private fun usersOf(memberIds: List<String>, groupId: String, myId: String): List<Group.User> =
        memberIds
            .filterNot { it.equals(myId, ignoreCase = true) }
            .mapNotNull { TestAccounts.find(it)?.toGroupUser(groupId) }

    /** DB 를 읽어 정렬 + 펼침상태를 입힌다. */
    private fun refreshFromDb(myId: String) {
        val groups = mapper.sortGroups(dbQueries.selectGroupsByMyId(myId).executeAsList())
            .map { entity ->
                val users = mapper.sortGroupUsers(
                    dbQueries.selectUsersByGroupId(myId, entity.groupId).executeAsList()
                        .map { mapper.toGroupUser(it) }
                )
                mapper.toGroup(entity, users).copy(
                    isExpanded = settings.getBoolean(
                        SettingsKeys.KEY_GROUP_EXPANDED_PREFIX + entity.groupId, false
                    )
                )
            }
        _groups.value = groups
    }
}

private fun TestAccounts.Account.toGroupUser(groupId: String) = Group.User(
    groupId = groupId,
    userId = userId,
    userName = userName,
    email = email,
    phoneNumber = phoneNumber,
    birthday = birthday,
    departmentName = organName,
    positionName = positionName,
    positionSortCode = positionSortCode,
    statusMessage = statusMessage,
    pcStatus = pcStatus,
    mobileStatus = mobileStatus,
    etcStatus = "0",
    userType = "0",
)
