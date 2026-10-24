package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.ui.login.LoginActions
import com.eunilsung.talk.ui.login.LoginStatus
import com.eunilsung.talk.ui.login.LoginViewModel
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 로그인 화면의 상태 — 진짜 서버의 답으로 성공·오류가 갈리고, 자동 로그인에 따라 다음 시작이 달라지는지.
 * 자동 로그인은 저장된 토큰을 서버에 확인해 들어가고, 저장한 비밀번호를 입력칸에 채우지 않는다.
 *
 * 이 도구의 로그인과 따로 도는 기기([RealServerHarness.otherDeviceLogin])로 같은 계정에 로그인한다.
 * 그래서 시나리오가 로그인하고 나가도 다른 묶음의 연결은 그대로다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelScenarioTest {

    /** ViewModel 은 메인 스레드에서 돈다. 테스트에는 메인 스레드가 없어 그 대역([viewModelMainThread])을 세운다. */
    @BeforeTest
    fun setUp() = Dispatchers.setMain(viewModelMainThread)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun 로그인화면() = functionalSuite("로그인화면") { harness ->
        listOf(
            scenario("오류와성공") {
                val viewModel = harness.loginViewModel(harness.otherDeviceLogin())

                step("빈 칸으로 누르면 오류 상태가 된다") {
                    viewModel.onAction(LoginActions.OnLoginClick(Login.LoginRequest()))
                    awaitTrue("오류 상태") { viewModel.uiState.value.status is LoginStatus.Error }
                }
                step("틀린 비밀번호는 서버가 거절해 오류 상태가 된다") {
                    viewModel.onAction(LoginActions.OnResetState)
                    viewModel.onAction(LoginActions.OnLoginClick(Login.LoginRequest(RealServerHarness.ME, "틀린-비밀번호")))
                    awaitTrue("오류 상태") { viewModel.uiState.value.status is LoginStatus.Error }
                }
                step("맞는 비밀번호로 누르면 성공 상태가 된다") {
                    viewModel.onAction(LoginActions.OnLoginClick(Login.LoginRequest(RealServerHarness.ME, RealServerHarness.PASSWORD)))
                    awaitTrue("성공 상태") { viewModel.uiState.value.status == LoginStatus.Success }
                }
            },
            scenario("저장하지않은로그인") {
                val device = MapSettings()

                step("자동 로그인을 켜지 않으면 다시 켰을 때 아이디만 채워진 입력칸이 뜬다") {
                    val first = harness.loginViewModel(harness.otherDeviceLogin(device))
                    first.onAction(LoginActions.OnLoginClick(Login.LoginRequest(RealServerHarness.ME, RealServerHarness.PASSWORD)))
                    awaitTrue("첫 로그인 성공") { first.uiState.value.status == LoginStatus.Success }

                    val restarted = harness.loginViewModel(harness.otherDeviceLogin(device))
                    requireEquals(RealServerHarness.ME, restarted.uiState.value.input.id, "채워진 아이디")
                    requireEquals("", restarted.uiState.value.input.pw, "채워진 비밀번호")
                    restarted.onAction(LoginActions.OnStart)
                    awaitTrue("입력칸이 뜬다") { restarted.uiState.value.status == LoginStatus.Idle }
                }
            },
            scenario("자동로그인과로그아웃") {
                val device = MapSettings()
                val restartedDevice = harness.otherDeviceLogin(device)
                lateinit var restarted: LoginViewModel

                step("자동 로그인을 켜고 들어가면 다시 켰을 때 비밀번호 없이 토큰으로 들어간다") {
                    val first = harness.loginViewModel(harness.otherDeviceLogin(device))
                    first.onAction(LoginActions.OnToggleSavePw(true))
                    first.onAction(
                        LoginActions.OnLoginClick(Login.LoginRequest(RealServerHarness.ME, RealServerHarness.PASSWORD, isSavePw = true))
                    )
                    awaitTrue("첫 로그인 성공") { first.uiState.value.status == LoginStatus.Success }
                    require(device.keys.none { device.getStringOrNull(it) == RealServerHarness.PASSWORD }, "비밀번호가 기기에 남았다")

                    restarted = harness.loginViewModel(restartedDevice)
                    requireEquals(RealServerHarness.ME, restarted.uiState.value.input.id, "채워진 아이디")
                    requireEquals("", restarted.uiState.value.input.pw, "채워진 비밀번호")
                    restarted.onAction(LoginActions.OnStart)
                    awaitTrue("입력 없이 들어간다") { restarted.uiState.value.status == LoginStatus.Success }
                }
                /** 로그아웃은 내 정보를 비운다. 뒤에 다른 시나리오가 오지 않게 마지막에 두고, 끝나면 되살린다. */
                step("로그아웃하면 입력칸으로 돌아오고 비밀번호 칸은 비어 있다") {
                    restartedDevice.logout()
                    awaitTrue("입력칸으로 돌아온다") { restarted.uiState.value.status == LoginStatus.Idle }
                    requireEquals("", restarted.uiState.value.input.pw, "남은 비밀번호")
                    requireEquals(RealServerHarness.ME, restarted.uiState.value.input.id, "남은 아이디")
                    require(harness.signIn() is Login.LoginResult.Success, "이 도구의 로그인을 되살리지 못했다")
                }
            },
        )
    }
}
