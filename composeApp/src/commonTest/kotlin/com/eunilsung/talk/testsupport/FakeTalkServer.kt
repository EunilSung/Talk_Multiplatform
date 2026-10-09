package com.eunilsung.talk.testsupport

import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.UserDto

/**
 * 서버 대역. 계정 하나(`test1` / `1234`)만 알고, 로그인할 때마다 다른 토큰을 내준다.
 *
 * [isReachable] 을 끄면 서버에 닿지 못한 상황을, [revoked] 로는 어떤 토큰이 지워졌는지를 본다.
 */
class FakeTalkServer : TalkServer {

    var isReachable = true
    var isTokenValid = true
    val revoked = mutableListOf<String>()
    private var issued = 0

    override suspend fun login(id: String, password: String): ServerResult<LoginResponse> {
        if (!isReachable) return ServerResult.Unreachable
        if (id != USER.id || password != PASSWORD) {
            return ServerResult.Rejected(401, ApiErrorCode.INVALID_CREDENTIALS)
        }
        return ServerResult.Success(LoginResponse(token = "token-${++issued}", user = USER))
    }

    override suspend fun logout(token: String) {
        revoked += token
    }

    override suspend fun me(): ServerResult<UserDto> = when {
        !isReachable -> ServerResult.Unreachable
        !isTokenValid -> ServerResult.Rejected(401, ApiErrorCode.UNAUTHORIZED)
        else -> ServerResult.Success(USER)
    }

    companion object {
        const val PASSWORD = "1234"
        val USER = UserDto(id = "test1", name = "김민준", organName = "개발1팀", positionName = "팀장")
    }
}
