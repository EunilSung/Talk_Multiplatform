package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.local.LocalSecret
import com.eunilsung.talk.data.remote.server.AuthTokenStore
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.repository.SettingsKeys.KEY_ID
import com.eunilsung.talk.data.repository.SettingsKeys.KEY_PW
import com.eunilsung.talk.data.repository.SettingsKeys.KEY_SAVE_PW
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.UserDto
import com.eunilsung.talk.util.Log
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.login_invalid_credentials
import multiplatformtalk.composeapp.generated.resources.no_server_response
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * 서버에 로그인하는 저장소.
 *
 * 비밀번호는 로그인 요청에 한 번만 실리고, 그 뒤로는 서버가 내준 토큰이 신원을 대신한다.
 */
class LoginRepositoryImpl(
    private val settings: Settings,
    private val server: TalkServer,
    private val tokenStore: AuthTokenStore,
) : LoginRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    override val isForeground: MutableStateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun requestLogin(input: Login.LoginRequest): Login.LoginResult {
        if (input.id.isBlank() || input.pw.isBlank()) {
            return Login.LoginResult.Error(text(Res.string.login_invalid_credentials))
        }

        val previousToken = tokenStore.token()
        return when (val result = server.login(input.id, input.pw)) {
            is ServerResult.Success -> {
                tokenStore.save(result.value.token)
                applyToMyInfo(result.value.user)
                settings.putString(KEY_ID, result.value.user.id)
                if (isSavePw()) settings.putString(KEY_PW, LocalSecret.encrypt(input.pw)) else settings.remove(KEY_PW)
                _isConnected.value = true
                _isLoggedIn.value = true
                revokeLater(previousToken)
                Log.message("[Login] success — ${result.value.user.id}")
                Login.LoginResult.Success
            }
            is ServerResult.Rejected -> {
                Log.message("[Login] rejected — ${result.status} ${result.code}")
                Login.LoginResult.Error(text(Res.string.login_invalid_credentials))
            }
            ServerResult.Unreachable -> Login.LoginResult.Error(text(Res.string.no_server_response))
        }
    }

    /** 같은 계정으로 여러 기기에서 쓰는 것을 막지 않는다. 일반 로그인과 같다. */
    override suspend fun duplicateLogin(input: Login.LoginRequest): Login.LoginResult =
        requestLogin(input)

    override fun logout() {
        Log.message("[Login] logout — ${Config.MyInfo.userId}")
        revokeLater(tokenStore.token())
        tokenStore.clear()
        settings.remove(KEY_PW)
        clearMyInfo()
        _isLoggedIn.value = false
    }

    override fun getSavedId(): String? = settings.getStringOrNull(KEY_ID)

    override fun getSavedPw(): String? =
        if (isSavePw()) settings.getStringOrNull(KEY_PW)?.let(LocalSecret::decrypt) else null

    override fun isSavePw(): Boolean = settings.getBoolean(KEY_SAVE_PW, false)

    override fun setSavePw(isSave: Boolean) {
        settings.putBoolean(KEY_SAVE_PW, isSave)
        if (!isSave) settings.remove(KEY_PW)
    }

    /**
     * 앱이 다시 앞으로 왔을 때 토큰이 아직 유효한지 서버에 묻는다.
     *
     * 서버가 토큰을 모른다고 답하면 로그아웃한다. 서버에 닿지 못한 것은 로그아웃 사유가 아니다 —
     * 연결 끊김 표시만 켜고 로그인 상태는 둔다.
     */
    override fun checkConnectionAndRelogin() {
        if (!_isLoggedIn.value) return
        repositoryScope.launch {
            when (val result = server.me()) {
                is ServerResult.Success -> _isConnected.value = true
                is ServerResult.Rejected -> {
                    _isConnected.value = true
                    if (result.code == ApiErrorCode.UNAUTHORIZED) logout()
                }
                ServerResult.Unreachable -> _isConnected.value = false
            }
        }
    }

    /**
     * 더 쓰지 않는 토큰을 서버에서 지운다.
     *
     * 지우지 않으면 로그인할 때마다 토큰이 하나씩 쌓인다. 실패해도 앱 동작에는 영향이 없어 기다리지 않는다.
     */
    private fun revokeLater(token: String) {
        if (token.isBlank()) return
        repositoryScope.launch { server.logout(token) }
    }

    /** 화면에 보여 줄 문구. 리소스를 읽을 수 없는 환경(JVM 단위 테스트)에서는 빈 문자열이 된다. */
    private suspend fun text(resource: StringResource): String =
        runCatching { getString(resource) }.getOrDefault("")

    private fun applyToMyInfo(user: UserDto) {
        Config.MyInfo.apply {
            userId = user.id
            password = ""
            userName = user.name
            email = user.email
            phoneNumber = user.phoneNumber
            birthday = user.birthday
            organName = user.organName
            positionName = user.positionName
            statusMessage = user.statusMessage
            pcStatus = STATUS_ONLINE
            mobileStatus = STATUS_ONLINE
            etcStatus = "0"
            userType = "0"
            maxGroupListNum = MAX_GROUP_LIST
            maxGroupMemberNum = MAX_GROUP_MEMBER
        }
    }

    private fun clearMyInfo() {
        Config.MyInfo.apply {
            userId = ""
            password = ""
            userName = ""
            email = ""
            phoneNumber = ""
            birthday = ""
            organName = ""
            positionName = ""
            statusMessage = ""
            pcStatus = ""
            mobileStatus = ""
            etcStatus = ""
            userType = ""
        }
    }

    private companion object {
        const val STATUS_ONLINE = "1"
        const val MAX_GROUP_LIST = "20"
        const val MAX_GROUP_MEMBER = "100"
    }
}
