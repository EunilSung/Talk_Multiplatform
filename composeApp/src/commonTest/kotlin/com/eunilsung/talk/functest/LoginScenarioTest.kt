package com.eunilsung.talk.functest

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.remote.server.AuthTokenStore
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServerClient
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.russhwolf.settings.MapSettings
import kotlin.test.Test

/** 로그인 — 토큰을 받아 내 정보가 채워지는지, 틀린 비밀번호와 끝난 로그인이 거절되는지. */
class LoginScenarioTest {

    @Test
    fun 로그인() = functionalSuite("로그인") { harness ->
        listOf(
            scenario("로그인상태") {
                step("로그인하면 내 정보가 채워진다") {
                    require(harness.login.isLoggedIn.value, "isLoggedIn 이 false 다")
                    requireEquals(RealServerHarness.ME, Config.MyInfo.userId, "내 아이디")
                    require(Config.MyInfo.userName.isNotBlank(), "내 이름이 비어 있다")
                }
                step("받은 토큰으로 서버가 나를 알아본다") {
                    requireEquals(RealServerHarness.ME, harness.server.me().must("내 정보 조회").id, "서버가 아는 나")
                }
            },
            scenario("틀린비밀번호") {
                /**
                 * 로그인에 쓰지 않는 계정으로 한 번만 틀린다. 연달아 틀리면 그 아이디가 잠기는데,
                 * 바로 뒤에 맞게 로그인하면 횟수가 지워져 다음 실행에 남지 않는다.
                 */
                val client = TalkServerClient(functionalTestHttpClient(), AuthTokenStore(MapSettings()), functionalTestServerUrl()!!)
                step("틀린 비밀번호는 거절된다") {
                    val result = client.login(SPARE_ACCOUNT, "틀린-비밀번호")
                    require(result is ServerResult.Rejected, "거절되지 않았다: $result")
                    requireEquals(ApiErrorCode.INVALID_CREDENTIALS, (result as ServerResult.Rejected).code, "거절 사유")
                }
                step("맞는 비밀번호로는 들어간다") {
                    val session = client.login(SPARE_ACCOUNT, RealServerHarness.PASSWORD).must("로그인")
                    client.logout(session.token)
                }
            },
            scenario("로그아웃") {
                val peer = harness.peer(RealServerHarness.THIRD)
                step("로그아웃한 토큰은 더 통하지 않는다") {
                    peer.client.me().must("로그아웃 전 조회")
                    peer.client.logout(peer.token)
                    val after = peer.client.me()
                    require(after is ServerResult.Rejected && after.status == 401, "끝난 토큰이 아직 통한다: $after")
                }
            },
        )
    }

    private companion object {
        const val SPARE_ACCOUNT = "test9"
    }
}
