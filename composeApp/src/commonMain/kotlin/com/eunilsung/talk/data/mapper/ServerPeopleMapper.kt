package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.domain.model.DEFAULT_GROUP_ALINE_CODE
import com.eunilsung.talk.domain.model.DEFAULT_GROUP_ID
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.shared.api.ContactGroupDto
import com.eunilsung.talk.shared.api.UserDto

/** 서버의 사람·연락처 그룹과 화면이 쓰는 모델 사이의 변환. */
class ServerPeopleMapper {

    /** 서버 사용자 → 그룹 화면의 사람. 접속 여부는 모바일 상태 칸에 싣는다. */
    fun toGroupUser(user: UserDto, groupId: String) = Group.User(
        groupId = groupId,
        userId = user.id,
        userName = user.name,
        email = user.email,
        phoneNumber = user.phoneNumber,
        birthday = user.birthday,
        departmentName = user.organName,
        positionName = user.positionName,
        positionSortCode = user.positionSort.toString().padStart(2, '0'),
        statusMessage = user.statusMessage,
        pcStatus = STATUS_OFFLINE,
        mobileStatus = if (user.isOnline) STATUS_ONLINE else STATUS_OFFLINE,
        etcStatus = "0",
        userType = "0",
    )

    /**
     * 서버의 사용자·내그룹 → 그룹 화면의 그룹 목록. [myProfile] 다음에 기본그룹, 그 뒤에 내가 만든 그룹이 온다.
     *
     * 어느 그룹에도 넣지 않은 사람은 기본그룹에 모은다. 그룹을 하나도 만들지 않아도 사람을 찾을 수 있어야 한다.
     */
    fun toGroups(
        myId: String,
        myProfile: Group.Item,
        users: List<UserDto>,
        groups: List<ContactGroupDto>,
    ): List<Group.Item> {
        val others = users.filterNot { it.id.equals(myId, ignoreCase = true) }.associateBy { it.id }
        val grouped = groups.flatMap { it.memberIds }.toSet()

        val defaultGroup = Group.Item(
            id = DEFAULT_GROUP_ID,
            name = "",
            alineCode = DEFAULT_GROUP_ALINE_CODE,
            userData = others.values.filterNot { it.id in grouped }.map { toGroupUser(it, DEFAULT_GROUP_ID) },
        )
        val custom = groups.map { group ->
            Group.Item(
                id = group.id,
                name = group.name,
                alineCode = group.sort.toString(),
                userData = group.memberIds.mapNotNull { member -> others[member]?.let { toGroupUser(it, group.id) } },
            )
        }
        return listOf(myProfile, defaultGroup) + custom
    }

    /** 내가 만든 그룹 → 서버에 올릴 모양. 정렬 코드가 숫자가 아니면 목록에서의 자리([index])를 쓴다. */
    fun toContactGroupDto(index: Int, group: Group.Item) = ContactGroupDto(
        id = group.id,
        name = group.name,
        sort = group.alineCode.toIntOrNull() ?: index,
        memberIds = group.userData.mapNotNull { it.userId },
    )

    /** 서버 사용자 → 프로필 화면. 회사 코드는 서버가 주지 않아 [companyId] 로 받는다. */
    fun toProfile(user: UserDto, companyId: String) = UserProfile(
        userId = user.id,
        name = user.name,
        position = user.positionName,
        department = user.organName,
        mobileNum = user.phoneNumber,
        email = user.email,
        motto = user.statusMessage,
        companyId = companyId,
    )

    companion object {
        const val STATUS_ONLINE = "1"
        const val STATUS_OFFLINE = "0"
    }
}
