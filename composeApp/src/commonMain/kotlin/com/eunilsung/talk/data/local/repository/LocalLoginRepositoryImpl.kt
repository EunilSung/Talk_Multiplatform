package com.eunilsung.talk.data.local.repository

import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.StringResource
import multiplatformtalk.composeapp.generated.resources.login_invalid_credentials
import multiplatformtalk.composeapp.generated.resources.login_pw_required
import multiplatformtalk.composeapp.generated.resources.login_id_required
import multiplatformtalk.composeapp.generated.resources.Res
import com.russhwolf.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.eunilsung.talk.Config
import com.eunilsung.talk.data.repository.SettingsKeys.KEY_ID
import com.eunilsung.talk.data.repository.SettingsKeys.KEY_PW
import com.eunilsung.talk.data.repository.SettingsKeys.KEY_SAVE_PW
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.data.local.seed.TestAccounts

/** [TestAccounts] 로만 인증하는 로그인 저장소. */
class LocalLoginRepositoryImpl(
    private val settings: Settings,
) : LoginRepository {

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isForeground = MutableStateFlow(true)
    override val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    override fun setForeground(isForeground: Boolean) {
        _isForeground.value = isForeground
    }

    override suspend fun requestLogin(input: Login.LoginRequest): Login.LoginResult {
        /** 로딩 상태 전환이 보이도록 최소 지연. */
        delay(LOGIN_DELAY_MS)

        if (input.id.isBlank()) return Login.LoginResult.Error(text(Res.string.login_id_required))
        if (input.pw.isBlank()) return Login.LoginResult.Error(text(Res.string.login_pw_required))

        val account = TestAccounts.find(input.id)
        if (account == null || input.pw != TestAccounts.PASSWORD) {
            return Login.LoginResult.Error(text(Res.string.login_invalid_credentials))
        }

        applyToMyInfo(account, input.pw)

        settings.putString(KEY_ID, account.userId)
        if (isSavePw()) settings.putString(KEY_PW, input.pw) else settings.remove(KEY_PW)

        _isLoggedIn.value = true
        Log.message("[Login/Local] success — ${account.userId} (${account.userName})")
        return Login.LoginResult.Success
    }

    /** 중복 로그인은 일반 로그인과 동일 처리. */
    override suspend fun duplicateLogin(input: Login.LoginRequest): Login.LoginResult =
        requestLogin(input)

    override fun logout() {
        Log.message("[Login/Local] logout — ${Config.MyInfo.userId}")
        settings.putString(KEY_PW, "")
        clearMyInfo()
        _isLoggedIn.value = false
    }

    override fun getMyUser(): User = User(id = Config.MyInfo.userId, name = Config.MyInfo.userName)

    override fun getSavedId(): String? = settings.getStringOrNull(KEY_ID)

    override fun getSavedPw(): String? =
        if (isSavePw()) settings.getStringOrNull(KEY_PW) else null

    override fun isSavePw(): Boolean = settings.getBoolean(KEY_SAVE_PW, false)

    override fun setSavePw(isSave: Boolean) {
        settings.putBoolean(KEY_SAVE_PW, isSave)
        if (!isSave) settings.remove(KEY_PW)
    }

    /** 소켓이 없어 끊길 연결도 없다. */
    override fun checkConnectionAndRelogin() = Unit

    /** 로그인 성공 시 [Config.MyInfo] 전역 상태를 채운다. */
    private fun applyToMyInfo(account: TestAccounts.Account, password: String) {
        Config.MyInfo.apply {
            userId = account.userId
            this.password = password
            userName = account.userName
            email = account.email
            phoneNumber = account.phoneNumber
            birthday = account.birthday
            organName = account.organName
            positionName = account.positionName
            statusMessage = account.statusMessage
            pcStatus = account.pcStatus
            mobileStatus = account.mobileStatus
            etcStatus = "0"
            userType = "0"
            maxGroupListNum = "20"
            maxGroupMemberNum = "100"
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
        const val LOGIN_DELAY_MS = 400L
    }

    /** 화면에 보여 줄 문구. 리소스를 읽을 수 없는 환경(JVM 단위 테스트)에서는 빈 문자열이 된다. */
    private suspend fun text(resource: StringResource): String =
        runCatching { getString(resource) }.getOrDefault("")
}
