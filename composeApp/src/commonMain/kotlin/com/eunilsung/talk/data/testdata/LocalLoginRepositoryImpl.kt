package com.eunilsung.talk.data.testdata

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
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.util.Log

/** [TestAccounts] 로만 인증하는 로그인 저장소. */
class LocalLoginRepositoryImpl(
    private val settings: Settings,
) : LoginRepository {

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    override val isForeground: MutableStateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun requestLogin(input: Login.LoginRequest): Login.LoginResult {
        // 로딩 상태 전환이 보이도록 최소 지연.
        delay(LOGIN_DELAY_MS)

        if (input.id.isBlank()) return Login.LoginResult.Error("아이디를 입력해 주세요")
        if (input.pw.isBlank()) return Login.LoginResult.Error("비밀번호를 입력해 주세요")

        val account = TestAccounts.find(input.id)
            ?: return Login.LoginResult.Error("가입되지 않은 아이디입니다")

        if (input.pw != TestAccounts.PASSWORD) {
            return Login.LoginResult.Error("비밀번호가 일치하지 않습니다")
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
}
