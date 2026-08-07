package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.Config
import com.eunilsung.talk.db.GroupEntity
import com.eunilsung.talk.db.GroupUserEntity
import com.eunilsung.talk.domain.model.MY_PROFILE_GROUP_ID
import com.eunilsung.talk.domain.model.Group

class GroupMapper {

    /** 명시적 값으로 그룹 생성. */
    fun toGroup(id: String, name: String, alineCode: String): Group.Item {
        return Group.Item(
            id = id,
            name = name,
            alineCode = alineCode,
            userData = emptyList()
        )
    }

    /** 내 프로필 그룹 — 항상 최상단. name 은 비워두고 UI 에서 표시. */
    fun toMyProfileGroup(): Group.Item {
        return Group.Item(
            id = MY_PROFILE_GROUP_ID,
            name = "",
            alineCode = Config.GroupType.BASE_GROUP_MYPROFILE,
            userData = emptyList()
        )
    }

    /** 내 프로필 그룹의 단일 사용자 = 본인. */
    fun toMyProfileUser(): Group.User {
        return Group.User(
            groupId = MY_PROFILE_GROUP_ID,
            userId = Config.MyInfo.userId,
            userName = Config.MyInfo.userName,
            email = Config.MyInfo.email,
            phoneNumber = Config.MyInfo.phoneNumber,
            birthday = Config.MyInfo.birthday,
            departmentName = Config.MyInfo.organName,
            positionName = Config.MyInfo.positionName,
            statusMessage = Config.MyInfo.statusMessage,
            etcStatus = Config.MyInfo.etcStatus,
            pcStatus = Config.MyInfo.pcStatus,
            mobileStatus = Config.MyInfo.mobileStatus,
            userType = Config.MyInfo.userType
        )
    }

    companion object {
    }

    /** 그룹 리스트 정렬. */
    fun sortGroups(groups: List<GroupEntity>): List<GroupEntity> {
        return groups.sortedWith { model1, model2 ->
            val code1 = model1.alineCode ?: ""
            val code2 = model2.alineCode ?: ""

            when {
                code1 == Config.GroupType.BASE_GROUP_MYPROFILE -> -1
                code2 == Config.GroupType.BASE_GROUP_MYPROFILE -> 1
                code1 == Config.GroupType.BASE_MYGRP_ID -> -1
                code2 == Config.GroupType.BASE_MYGRP_ID -> 1
                code1 == Config.GroupType.BASE_GRP_ID -> 1
                code2 == Config.GroupType.BASE_GRP_ID -> -1
                else -> {
                    if (code1 == "" || code2 == "" || code1 == code2) {
                        (model1.name ?: "").compareTo(model2.name ?: "")
                    } else {
                        code1.compareTo(code2)
                    }
                }
            }
        }
    }

    /** 그룹 사용자 정렬 — positionSortCode → positionCode → userName 순 (값 없으면 뒤로). */
    fun sortGroupUsers(users: List<Group.User>): List<Group.User> {
        return users.sortedWith(
            compareBy<Group.User>(
                { it.positionSortCode.isNullOrBlank() },
                { it.positionSortCode.orEmpty() },
                { it.positionCode.isNullOrBlank() },
                { it.positionCode.orEmpty() },
                { it.userName.orEmpty() }
            )
        )
    }

    /** Entity -> Domain 변환. */
    fun toGroup(entity: GroupEntity, users: List<Group.User>): Group.Item {
        return Group.Item(
            id = entity.groupId,
            name = entity.name ?: "",
            alineCode = entity.alineCode ?: "",
            userData = users,
            isExpanded = false
        )
    }

    fun toGroupUser(entity: GroupUserEntity): Group.User {
        return Group.User(
            groupId = entity.groupId,
            userId = entity.userId,
            alias = entity.alias,
            isAlarm = entity.isAlarm,
            userName = entity.userName,
            pcStatus = entity.pcStatus,
            phoneNumber = entity.phoneNumber,
            email = entity.email,
            birthday = entity.birthday,
            solarLunar = entity.solarLunar,
            departmentName = entity.departmentName,
            positionName = entity.positionName,
            responsibilities = entity.responsibilities,
            statusMessage = entity.statusMessage,
            positionCode = entity.positionCode,
            positionSortCode = entity.positionSortCode,
            employeeNumber = entity.employeeNumber,
            etcStatus = entity.etcStatus,
            userType = entity.userType,
            departmentTopCode = entity.departmentTopCode,
            offset21 = entity.offset21,
            offset22 = entity.offset22,
            offset23 = entity.offset23,
            offset24 = entity.offset24,
            offset25 = entity.offset25,
            offset26 = entity.offset26,
            pcMobileStatus = entity.pcMobileStatus,
            mobileStatus = entity.mobileStatus
        )
    }

    /** Domain -> Entity 변환. */
    fun toEntity(myId: String, item: Group.Item): GroupEntity {
        return GroupEntity(
            myId = myId,
            groupId = item.id,
            name = item.name,
            alineCode = item.alineCode
        )
    }

    fun toEntity(myId: String, user: Group.User): GroupUserEntity {
        return GroupUserEntity(
            myId = myId,
            groupId = user.groupId ?: "",
            userId = user.userId ?: "",
            startOffset = user.startOffset,
            alias = user.alias,
            isAlarm = user.isAlarm,
            userName = user.userName ?: "",
            pcStatus = user.pcStatus,
            phoneNumber = user.phoneNumber,
            email = user.email,
            birthday = user.birthday,
            solarLunar = user.solarLunar,
            departmentName = user.departmentName,
            positionName = user.positionName,
            responsibilities = user.responsibilities,
            statusMessage = user.statusMessage,
            positionCode = user.positionCode,
            positionSortCode = user.positionSortCode,
            employeeNumber = user.employeeNumber,
            etcStatus = user.etcStatus,
            userType = user.userType,
            departmentTopCode = user.departmentTopCode,
            offset21 = user.offset21,
            offset22 = user.offset22,
            offset23 = user.offset23,
            offset24 = user.offset24,
            offset25 = user.offset25,
            offset26 = user.offset26,
            pcMobileStatus = user.pcMobileStatus,
            mobileStatus = user.mobileStatus
        )
    }
}
