package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.GroupMapper
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.remote.server.UserDirectory
import com.eunilsung.talk.data.remote.server.valueOrNull
import com.eunilsung.talk.data.sample.LocalGroupRepositoryImpl
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.isDefaultGroup
import com.eunilsung.talk.domain.model.isMyProfileGroup
import com.eunilsung.talk.domain.repository.AddUserResult
import com.eunilsung.talk.domain.repository.GroupRepository
import com.eunilsung.talk.shared.api.ContactGroupDto
import com.eunilsung.talk.shared.api.UserDto

/**
 * 서버의 사용자와 내그룹을 로컬 DB 에 받아 두고 보여 주는 저장소.
 *
 * 화면은 늘 로컬 DB 를 본다([local]). 내가 만든 그룹은 서버에 보관하고, 어느 그룹에도 넣지 않은
 * 사람은 "기본 그룹"에 모아 보여 준다 — 그룹을 하나도 만들지 않아도 사람을 찾을 수 있어야 한다.
 */
class GroupRepositoryImpl(
    private val local: LocalGroupRepositoryImpl,
    private val server: TalkServer,
    private val directory: UserDirectory,
    private val mapper: GroupMapper,
) : GroupRepository by local {

    /** 서버에서 사용자와 그룹을 받아 로컬을 바꾼다. 둘 중 하나라도 못 받으면 가지고 있던 것을 그대로 둔다. */
    override suspend fun fetchGroups() {
        val myId = Config.MyInfo.userId
        if (myId.isBlank()) return
        val users = server.users().valueOrNull()
        val groups = server.contactGroups().valueOrNull()
        if (users == null || groups == null) {
            local.fetchGroups()
            return
        }
        if (Config.MyInfo.userId != myId) return
        directory.replace(users)
        local.replaceGroups(buildGroups(myId, users, groups))
    }

    override suspend fun copyUserToGroup(userId: String, targetGroupId: String): AddUserResult {
        var result = AddUserResult.FAILED
        val isSaved = changeGroups { result = local.copyUserToGroup(userId, targetGroupId) }
        return if (result == AddUserResult.SUCCESS && !isSaved) AddUserResult.FAILED else result
    }

    override suspend fun removeUserFromGroup(userId: String, targetGroupId: String): Boolean {
        var isChanged = false
        return changeGroups { isChanged = local.removeUserFromGroup(userId, targetGroupId) } && isChanged
    }

    override suspend fun moveUserToGroup(userId: String, fromGroupId: String, toGroupId: String): Boolean {
        var isChanged = false
        return changeGroups { isChanged = local.moveUserToGroup(userId, fromGroupId, toGroupId) } && isChanged
    }

    override suspend fun renameGroup(groupId: String, newName: String): Boolean {
        var isChanged = false
        return changeGroups { isChanged = local.renameGroup(groupId, newName) } && isChanged
    }

    override suspend fun deleteGroup(groupId: String): Boolean {
        var isChanged = false
        return changeGroups { isChanged = local.deleteGroup(groupId) } && isChanged
    }

    override suspend fun createGroup(name: String): Boolean {
        var isChanged = false
        return changeGroups { isChanged = local.createGroup(name) } && isChanged
    }

    /**
     * 그룹을 고치고 그 결과를 서버에 올린다. 서버에 반영됐으면 true.
     *
     * 고치는 규칙은 [local] 에 이미 있어 그대로 쓰고, 고친 뒤의 내 그룹 전체를 서버에 보낸다. 서버가
     * 받아 주지 않으면 고치기 전으로 되돌린다. 반영된 뒤에는 서버 것으로 다시 그린다 — 그룹에서 뺀
     * 사람이 기본 그룹으로 돌아가는 것처럼, 서버 기준으로 다시 계산해야 맞는 것들이 있다.
     */
    private suspend fun changeGroups(change: suspend () -> Unit): Boolean {
        val before = local.currentGroups()
        change()
        val after = local.currentGroups()
        if (after == before) return true
        val customGroups = after.filterNot { it.isMyProfileGroup || it.isDefaultGroup }
        if (server.putContactGroups(customGroups.mapIndexed(::toDto)) !is ServerResult.Success) {
            local.replaceGroups(before)
            return false
        }
        fetchGroups()
        return true
    }

    private fun buildGroups(myId: String, users: List<UserDto>, groups: List<ContactGroupDto>): List<Group.Item> {
        val others = users.filterNot { it.id.equals(myId, ignoreCase = true) }.associateBy { it.id }
        val grouped = groups.flatMap { it.memberIds }.toSet()

        val myProfile = mapper.toMyProfileGroup().let { group ->
            group.copy(userData = listOf(mapper.toMyProfileUser().copy(groupId = group.id)))
        }
        val defaultGroup = Group.Item(
            id = DEFAULT_GROUP_ID,
            name = "",
            alineCode = DEFAULT_GROUP_ALINE_CODE,
            userData = others.values.filterNot { it.id in grouped }.map { it.toGroupUser(DEFAULT_GROUP_ID) },
        )
        val custom = groups.map { group ->
            Group.Item(
                id = group.id,
                name = group.name,
                alineCode = group.sort.toString(),
                userData = group.memberIds.mapNotNull { others[it]?.toGroupUser(group.id) },
            )
        }
        return listOf(myProfile, defaultGroup) + custom
    }

    private fun toDto(index: Int, group: Group.Item) = ContactGroupDto(
        id = group.id,
        name = group.name,
        sort = group.alineCode.toIntOrNull() ?: index,
        memberIds = group.userData.mapNotNull { it.userId },
    )

    companion object {
        /** 기본 그룹(어느 그룹에도 넣지 않은 사람들)의 고정 id 와 정렬 코드. 화면이 이 값으로 기본 그룹을 알아본다. */
        const val DEFAULT_GROUP_ID = "0"
        const val DEFAULT_GROUP_ALINE_CODE = "-3"

        const val STATUS_ONLINE = "1"
        const val STATUS_OFFLINE = "0"

        /** 서버 사용자 → 그룹 화면의 사람. 접속 여부는 모바일 상태 칸에 싣는다. */
        fun UserDto.toGroupUser(groupId: String) = Group.User(
            groupId = groupId,
            userId = id,
            userName = name,
            email = email,
            phoneNumber = phoneNumber,
            birthday = birthday,
            departmentName = organName,
            positionName = positionName,
            positionSortCode = positionSort.toString().padStart(2, '0'),
            statusMessage = statusMessage,
            pcStatus = STATUS_OFFLINE,
            mobileStatus = if (isOnline) STATUS_ONLINE else STATUS_OFFLINE,
            etcStatus = "0",
            userType = "0",
        )
    }
}
