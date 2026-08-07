package com.eunilsung.talk.domain.usecase

import kotlinx.coroutines.flow.Flow
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.repository.GroupRepository

/** 그룹 관련 유스케이스 묶음. */
data class GroupUseCases(
    val getGroups: GetGroupsUseCase,
    val fetchGroups: FetchGroupsUseCase,
    val toggleGroupExpanded: ToggleGroupExpandedUseCase,
    val removeUserFromGroup: RemoveUserFromGroupUseCase,
    val moveUserToGroup: MoveUserToGroupUseCase,
    val renameGroup: RenameGroupUseCase,
    val deleteGroup: DeleteGroupUseCase,
    val createGroup: CreateGroupUseCase
)

/** 그룹에서 사용자 삭제 */
class RemoveUserFromGroupUseCase(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(userId: String, targetGroupId: String): Boolean =
        groupRepository.removeUserFromGroup(userId, targetGroupId)
}

/** 그룹이동 */
class MoveUserToGroupUseCase(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(
        userId: String,
        fromGroupId: String,
        toGroupId: String
    ): Boolean = groupRepository.moveUserToGroup(userId, fromGroupId, toGroupId)
}

/** 그룹 이름변경 */
class RenameGroupUseCase(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String, newName: String): Boolean =
        groupRepository.renameGroup(groupId, newName)
}

/** 그룹 삭제 */
class DeleteGroupUseCase(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String): Boolean =
        groupRepository.deleteGroup(groupId)
}

/** 그룹 생성 */
class CreateGroupUseCase(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(name: String): Boolean =
        groupRepository.createGroup(name)
}

/** 그룹 펼침/닫힘 상태 변경 유스케이스. */
class ToggleGroupExpandedUseCase(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(groupId: String, isExpanded: Boolean) {
        groupRepository.toggleGroupExpanded(groupId, isExpanded)
    }
}

/** 그룹 리스트 관찰 유스케이스. */
class GetGroupsUseCase(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(): Flow<List<Group.Item>> = groupRepository.getGroups()
}

/** 그룹 리스트 갱신 요청 유스케이스. */
class FetchGroupsUseCase(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke() {
        groupRepository.fetchGroups()
    }
}
