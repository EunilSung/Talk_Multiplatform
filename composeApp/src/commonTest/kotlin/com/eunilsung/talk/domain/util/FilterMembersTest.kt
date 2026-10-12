package com.eunilsung.talk.domain.util

import com.eunilsung.talk.domain.model.Group
import kotlin.test.Test
import kotlin.test.assertEquals

/** 내 그룹과 초대 화면의 그룹 탭이 같은 규칙으로 사람을 찾는다 — 이름·초성·전화번호. */
class FilterMembersTest {

    private val groups = listOf(
        Group.Item(
            id = "0", name = "", alineCode = "-3",
            userData = listOf(
                Group.User(groupId = "0", userId = "test4", userName = "최지우", phoneNumber = "|||010-1000-0004"),
                Group.User(groupId = "0", userId = "test9", userName = "임채원", phoneNumber = "|||010-1000-0009"),
            ),
        ),
        Group.Item(
            id = "1", name = "개발팀", alineCode = "1",
            userData = listOf(Group.User(groupId = "1", userId = "test2", userName = "이서연", phoneNumber = "|||010-1000-0002")),
        ),
    )

    private fun ids(result: List<Group.Item>) = result.flatMap { g -> g.userData.map { it.userId } }

    @Test
    fun 이름_일부로_찾는다() = assertEquals(listOf("test4"), ids(groups.filterMembers("지우")))

    @Test
    fun 초성으로_찾는다() = assertEquals(listOf("test9"), ids(groups.filterMembers("ㅇㅊㅇ")))

    @Test
    fun 전화번호_숫자로_찾는다() = assertEquals(listOf("test2"), ids(groups.filterMembers("0002")))

    @Test
    fun 맞는_사람이_없는_그룹은_빠지고_남은_그룹은_펼친다() {
        val result = groups.filterMembers("이서연")
        assertEquals(listOf("1"), result.map { it.id })
        assertEquals(true, result.single().isExpanded)
    }

    @Test
    fun 검색어가_비면_그대로다() = assertEquals(groups, groups.filterMembers(" "))
}
