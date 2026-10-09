package com.eunilsung.talk.data.remote.server

import com.eunilsung.talk.shared.api.UserDto
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 서버에서 받은 사용자 목록을 들고 있는 곳.
 *
 * 그룹·프로필·대화방 참여자 화면이 같은 사람 정보를 쓴다. 화면마다 서버에 따로 물으면 같은 사람이
 * 화면마다 다르게 보일 수 있어, 마지막으로 받은 목록 하나를 함께 본다. 앱을 끄면 사라지고 다음
 * 로그인 때 다시 받는다.
 */
class UserDirectory {

    private val users = MutableStateFlow<Map<String, UserDto>>(emptyMap())

    fun find(userId: String): UserDto? = users.value[userId.trim().lowercase()]

    fun all(): List<UserDto> = users.value.values.toList()

    fun replace(list: List<UserDto>) {
        users.value = list.associateBy { it.id.lowercase() }
    }

    /** 한 사람의 정보만 새로 받았을 때 끼워 넣는다. */
    fun put(user: UserDto) {
        users.value = users.value + (user.id.lowercase() to user)
    }
}
