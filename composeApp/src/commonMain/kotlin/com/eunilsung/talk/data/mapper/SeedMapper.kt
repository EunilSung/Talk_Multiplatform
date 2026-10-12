package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.data.local.seed.TestAccounts
import com.eunilsung.talk.data.local.seed.TestGroups
import com.eunilsung.talk.domain.model.DEFAULT_GROUP_ALINE_CODE
import com.eunilsung.talk.domain.model.DEFAULT_GROUP_ID
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.UserProfile

/** 시연용 데이터([TestAccounts]·[TestGroups]) → 도메인 모델. 로컬 모드 저장소가 쓴다. */
class SeedMapper {

    /** 대화방 참여자. 이름이 비어 있으면 계정 이름으로 채우고, 계정에 없는 id 면 id 만 채운다. */
    fun toRoomUser(id: String, name: String): User {
        val account = TestAccounts.find(id)
        return User(
            id = id,
            name = name.ifBlank { account?.userName ?: id },
            departmentName = account?.organName,
            positionName = account?.positionName,
            presencePc = account?.pcStatus,
            presenceMobile = account?.mobileStatus,
        )
    }

    /** 그룹 화면의 사람. */
    fun toGroupUser(account: TestAccounts.Account, groupId: String) = Group.User(
        groupId = groupId,
        userId = account.userId,
        userName = account.userName,
        email = account.email,
        phoneNumber = account.phoneNumber,
        birthday = account.birthday,
        departmentName = account.organName,
        positionName = account.positionName,
        positionSortCode = account.positionSortCode,
        statusMessage = account.statusMessage,
        pcStatus = account.pcStatus,
        mobileStatus = account.mobileStatus,
        etcStatus = "0",
        userType = "0",
    )

    /** 기본그룹. 이름은 비워 두고 화면에서 붙인다. */
    fun toDefaultGroup(users: List<Group.User>) = Group.Item(
        id = DEFAULT_GROUP_ID,
        name = "",
        alineCode = DEFAULT_GROUP_ALINE_CODE,
        userData = users,
    )

    /** 사용자 그룹. */
    fun toGroup(seed: TestGroups.Seed, users: List<Group.User>) = Group.Item(
        id = seed.id,
        name = seed.name,
        alineCode = seed.alineCode,
        userData = users,
    )

    /** 프로필. 내선·팩스는 테스트 계정에 없는 값이라 비워 둔다. */
    fun toProfile(account: TestAccounts.Account, companyId: String) = UserProfile(
        userId = account.userId,
        name = account.userName,
        position = account.positionName,
        department = account.organName,
        mobileNum = account.phoneNumber,
        email = account.email,
        motto = account.statusMessage,
        extensionNum = "",
        localCallNum = "",
        faxNum = "",
        companyId = companyId,
    )
}
