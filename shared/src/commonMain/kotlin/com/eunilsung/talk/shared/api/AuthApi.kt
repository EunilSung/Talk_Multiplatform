package com.eunilsung.talk.shared.api

import kotlinx.serialization.Serializable

/** 로그인 요청. 비밀번호는 이 요청에만 실리고, 이후에는 토큰이 신원을 대신한다. */
@Serializable
data class LoginRequest(
    val id: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    /**
     * 이후 요청의 `Authorization: Bearer` 헤더에 실어 보낼 토큰.
     *
     * 여기서 한 번만 온다. 서버에는 해시만 남아 다시 알려 줄 수 없으니 앱이 보관해야 한다.
     */
    val token: String,
    val user: UserDto,
)

/** 사용자 한 명. 비밀번호 같은 비공개 값은 들어가지 않는다. */
@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val organName: String = "",
    val positionName: String = "",
    /** 그룹 안에서 사람을 세우는 순서. 직급이 높을수록 작다. */
    val positionSort: Int = 99,
    val email: String = "",
    val phoneNumber: String = "",
    val birthday: String = "",
    val statusMessage: String = "",
    /** 지금 앱이 서버에 붙어 있는지. 목록을 받은 순간의 값이다. */
    val isOnline: Boolean = false,
)

/** [ApiError.code] 로 오는 값들. 앱은 이 값으로 보여 줄 문구를 고른다. */
object ApiErrorCode {
    /** 아이디가 없거나 비밀번호가 틀렸다. 어느 쪽인지는 알려 주지 않는다. */
    const val INVALID_CREDENTIALS = "invalid_credentials"
    /** 토큰이 없거나 더 이상 유효하지 않다. 다시 로그인해야 한다. */
    const val UNAUTHORIZED = "unauthorized"
    const val BAD_REQUEST = "bad_request"
    /** 틀린 비밀번호가 연달아 들어와 잠시 로그인을 받지 않는다. 응답의 `Retry-After` 헤더가 남은 초다. */
    const val TOO_MANY_ATTEMPTS = "too_many_attempts"
}

@Serializable
data class UsersResponse(val users: List<UserDto> = emptyList())

/** 내그룹 하나 — 내가 묶어 둔 사람들. */
@Serializable
data class ContactGroupDto(
    val id: String,
    val name: String,
    val sort: Int = 0,
    val memberIds: List<String> = emptyList(),
)

/** 내그룹 전부. 바꿀 때도 이 모양으로 전체를 보낸다. */
@Serializable
data class ContactGroupsDto(val groups: List<ContactGroupDto> = emptyList())

/** 이 기기의 푸시 토큰 등록. [platform] 은 앱이 밝힌 플랫폼 이름이다(`ANDROID`, `IPHONE`). */
@Serializable
data class RegisterPushTokenRequest(
    val token: String,
    val platform: String,
)

/**
 * 푸시 data 에 실리는 키와 값.
 *
 * 앱의 푸시 수신 코드가 이 이름으로 읽는다. 서버와 앱이 같은 상수를 보게 해 이름이 어긋나지 않게 한다.
 */
object PushKeys {
    const val MSG = "msg"
    /** 알림을 눌렀을 때 열 대화방 id. */
    const val MSG_KEY = "msgkey"
    const val MSG_KIND = "msgkind"
    /** 대화 종류 — 앱의 대화 타입 코드. */
    const val MSG_TYPE = "msgtype"
    const val SENDER_NAME = "senderName"
    /** 받는 사람의 전체 안읽음 수. 앱 아이콘 배지에 쓴다. */
    const val UNREAD_COUNT = "unReadCount"
    const val CATEGORY_ID = "msgCategoryId"

    const val KIND_TALK = "TALK"
}
