package com.eunilsung.talk.ui.login

import com.eunilsung.talk.data.local.repository.LocalVersionCheckRepositoryImpl
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.usecase.DuplicateLoginUseCase
import com.eunilsung.talk.domain.usecase.GetMyUserUseCase
import com.eunilsung.talk.domain.usecase.GetSavedLoginUseCase
import com.eunilsung.talk.domain.usecase.LoginUseCase
import com.eunilsung.talk.domain.usecase.LoginUseCases
import com.eunilsung.talk.domain.usecase.LogoutUseCase
import com.eunilsung.talk.domain.usecase.ObserveLoginStateUseCase
import com.eunilsung.talk.domain.usecase.ResumeLoginUseCase
import com.eunilsung.talk.domain.usecase.SetSavePasswordUseCase
import com.eunilsung.talk.domain.usecase.VersionCheckUseCase
import com.eunilsung.talk.testsupport.FakeLoginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 로그인 화면의 상태 — 저장해 둔 비밀번호가 입력칸에 다시 나오지 않는지('보기'로 읽히지 않는지)와 자동 로그인.
 * 자동 로그인이 서버에 닿지 못하면 비밀번호를 다시 넣게 하지 않고 같은 토큰으로 다시 시도할 수 있다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    /** 자동 로그인을 켰고 아이디가 남아 있는 기기. [resumed] 는 남은 로그인으로 다시 들어간 결과다. */
    private class SavedDevice(var resumed: Login.LoginResult?) : FakeLoginRepository() {
        var resumeCount = 0
        val loginRequests = mutableListOf<Login.LoginRequest>()

        override fun getSavedId(): String = "test1"
        override fun isSavePw(): Boolean = true
        override suspend fun resumeLogin(): Login.LoginResult? {
            resumeCount++
            return resumed
        }

        override suspend fun requestLogin(input: Login.LoginRequest): Login.LoginResult {
            loginRequests += input
            isLoggedIn.value = true
            return Login.LoginResult.Success
        }
    }

    private fun viewModel(repository: FakeLoginRepository) = LoginViewModel(
        LoginUseCases(
            login = LoginUseCase(repository),
            logout = LogoutUseCase(repository),
            duplicateLogin = DuplicateLoginUseCase(repository),
            observeLoginState = ObserveLoginStateUseCase(repository),
            getSavedLogin = GetSavedLoginUseCase(repository),
            setSavePassword = SetSavePasswordUseCase(repository),
            getMyUser = GetMyUserUseCase(repository),
            resumeLogin = ResumeLoginUseCase(repository),
        ),
        VersionCheckUseCase(LocalVersionCheckRepositoryImpl()),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun 입력칸에는_아이디만_채우고_비밀번호는_채우지_않는다() {
        val viewModel = viewModel(SavedDevice(resumed = null))

        assertEquals("test1", viewModel.uiState.value.input.id)
        assertEquals("", viewModel.uiState.value.input.pw)
        assertTrue(viewModel.uiState.value.input.isSavePw)
    }

    @Test
    fun 남은_로그인이_있으면_입력칸을_거치지_않고_들어간다() = runTest {
        val device = SavedDevice(resumed = Login.LoginResult.Success)
        val viewModel = viewModel(device)

        viewModel.onAction(LoginActions.OnStart)
        advanceUntilIdle()

        assertEquals(LoginStatus.Success, viewModel.uiState.value.status)
        assertEquals(1, device.resumeCount)
        assertEquals(emptyList(), device.loginRequests)
        assertEquals("", viewModel.uiState.value.input.pw)
    }

    @Test
    fun 남은_로그인이_없으면_비밀번호가_빈_입력칸이_뜬다() = runTest {
        val viewModel = viewModel(SavedDevice(resumed = null))

        viewModel.onAction(LoginActions.OnStart)
        advanceUntilIdle()

        assertEquals(LoginStatus.Idle, viewModel.uiState.value.status)
        assertEquals("", viewModel.uiState.value.input.pw)
    }

    @Test
    fun 다시_들어가다_서버에_닿지_못하면_다시_시도를_묻고_비밀번호는_비어_있다() = runTest {
        val viewModel = viewModel(SavedDevice(resumed = Login.LoginResult.Error("연결 실패")))

        viewModel.onAction(LoginActions.OnStart)
        advanceUntilIdle()

        assertEquals(LoginStatus.ResumeFailed("연결 실패"), viewModel.uiState.value.status)
        assertEquals("", viewModel.uiState.value.input.pw)
    }

    @Test
    fun 서버에_닿지_못한_뒤_다시_시도하면_비밀번호_없이_남은_로그인으로_들어간다() = runTest {
        val device = SavedDevice(resumed = Login.LoginResult.Error("연결 실패"))
        val viewModel = viewModel(device)
        viewModel.onAction(LoginActions.OnStart)
        advanceUntilIdle()

        viewModel.onAction(LoginActions.OnRetryResume)
        advanceUntilIdle()
        assertEquals(LoginStatus.ResumeFailed("연결 실패"), viewModel.uiState.value.status)

        device.resumed = Login.LoginResult.Success
        viewModel.onAction(LoginActions.OnRetryResume)
        advanceUntilIdle()

        assertEquals(LoginStatus.Success, viewModel.uiState.value.status)
        assertEquals(3, device.resumeCount)
        assertEquals(emptyList(), device.loginRequests)
    }

    @Test
    fun 다시_시도할_때_남은_로그인이_없어졌으면_입력칸이_뜬다() = runTest {
        val device = SavedDevice(resumed = Login.LoginResult.Error("연결 실패"))
        val viewModel = viewModel(device)
        viewModel.onAction(LoginActions.OnStart)
        advanceUntilIdle()

        device.resumed = null
        viewModel.onAction(LoginActions.OnRetryResume)
        advanceUntilIdle()

        assertEquals(LoginStatus.Idle, viewModel.uiState.value.status)
        assertEquals(emptyList(), device.loginRequests)
    }

    @Test
    fun 다시_시도를_취소하면_입력칸이_뜨고_다시_묻지_않는다() = runTest {
        val device = SavedDevice(resumed = Login.LoginResult.Error("연결 실패"))
        val viewModel = viewModel(device)
        viewModel.onAction(LoginActions.OnStart)
        advanceUntilIdle()

        viewModel.onAction(LoginActions.OnResetState)
        advanceUntilIdle()

        assertEquals(LoginStatus.Idle, viewModel.uiState.value.status)
        assertEquals(1, device.resumeCount)
        assertEquals(emptyList(), device.loginRequests)
    }

    @Test
    fun 로그아웃하면_넣었던_비밀번호를_지우고_보기도_끈다() = runTest {
        val device = SavedDevice(resumed = null)
        val viewModel = viewModel(device)
        viewModel.onAction(LoginActions.OnPwChange("1234"))
        viewModel.onAction(LoginActions.OnTogglePasswordVisible)
        viewModel.onAction(LoginActions.OnLoginClick(viewModel.uiState.value.input))
        advanceUntilIdle()

        device.logout()
        advanceUntilIdle()

        assertEquals(LoginStatus.Idle, viewModel.uiState.value.status)
        assertEquals("", viewModel.uiState.value.input.pw)
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }
}
