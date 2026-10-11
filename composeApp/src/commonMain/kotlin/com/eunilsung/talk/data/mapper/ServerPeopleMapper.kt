package com.eunilsung.talk.data.mapper

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
