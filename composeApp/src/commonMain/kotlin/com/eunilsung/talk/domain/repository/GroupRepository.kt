package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.Flow
import com.eunilsung.talk.domain.model.Group

/** 사용자 그룹 추가 결과 — 3-state */
enum class AddUserResult {
    SUCCESS,
    /** 이미 그룹에 있는 사용자 (안내 문구용으로 구분). */
    ALREADY_EXISTS,
    FAILED
}

interface GroupRepository {
    fun getGroups(): Flow<List<Group.Item>>
    suspend fun fetchGroups()
    fun toggleGroupExpanded(groupId: String, isExpanded: Boolean)

    /** 사용자를 대상 그룹으로 추가. */
    suspend fun copyUserToGroup(userId: String, targetGroupId: String): AddUserResult

    /** 그룹에서 사용자 삭제. */
    suspend fun removeUserFromGroup(userId: String, targetGroupId: String): Boolean

    /** 사용자를 source 그룹에서 target 그룹으로 이동. */
    suspend fun moveUserToGroup(userId: String, fromGroupId: String, toGroupId: String): Boolean

    /** 그룹 이름변경. */
    suspend fun renameGroup(groupId: String, newName: String): Boolean

    /** 그룹 삭제 (소속 사용자 row 포함). */
    suspend fun deleteGroup(groupId: String): Boolean

    /** 그룹 생성. */
    suspend fun createGroup(name: String): Boolean
}

