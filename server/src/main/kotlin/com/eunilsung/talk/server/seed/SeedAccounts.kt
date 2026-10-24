package com.eunilsung.talk.server.seed

import com.eunilsung.talk.server.auth.PasswordHasher
import com.eunilsung.talk.server.repository.UserRepository
import com.eunilsung.talk.shared.api.UserDto

/**
 * 시연용 계정 `test1` ~ `test10`.
 *
 * 회원가입이 없는 쇼케이스라 계정을 서버가 미리 만들어 둔다. 앱의 로컬 모드가 쓰는 계정과
 * 같은 사람들이라, 서버 모드로 바꿔도 같은 아이디로 로그인된다.
 *
 * 비밀번호는 서버를 띄우는 쪽이 정한다. 이 코드에는 기본 비밀번호가 없다 — 운영 진입점은 `SEED_DEMO_ACCOUNTS=true` 와
 * `SEED_PASSWORD` 가 함께 있을 때만 계정을 만들고, 로컬 개발 서버(`:server:runLocal`)만 알려진 시연용 비밀번호를 쓴다.
 * 공개 저장소에 적힌 비밀번호로 배포한 서버의 모든 계정에 들어갈 수 있으면 안 된다.
 */
object SeedAccounts {

    /** 앞에 올수록 상위 직급. [UserDto.positionSort] 를 여기서 뽑는다. */
    private val POSITION_ORDER = listOf("부장", "차장", "팀장", "과장", "대리", "주임", "사원")

    val ALL: List<UserDto> = listOf(
        account(1, "김민준", "개발1팀", "팀장", "19850312", "코드 리뷰 중입니다"),
        account(2, "이서연", "개발1팀", "대리", "19920705", ""),
        account(3, "박도윤", "개발2팀", "과장", "19880921", "회의 중"),
        account(4, "최지우", "개발2팀", "사원", "19960214", ""),
        account(5, "정하윤", "기획팀", "차장", "19830618", "기획서 작성 중"),
        account(6, "강시우", "기획팀", "사원", "19970430", ""),
        account(7, "조유진", "디자인팀", "팀장", "19870104", "디자인 검토 요청 주세요"),
        account(8, "윤준서", "디자인팀", "주임", "19941123", ""),
        account(9, "임채원", "경영지원팀", "부장", "19790827", "외근 중"),
        account(10, "한지호", "경영지원팀", "사원", "19980509", ""),
    )

    /**
     * 시연용 계정의 비밀번호를 [password] 로 맞춘다. 없는 계정은 만들고, 이미 있는 계정의 비밀번호가 다르면 바꾼다.
     *
     * 이미 배포한 서버도 `SEED_PASSWORD` 를 새 값으로 주고 다시 띄우면 예전 비밀번호가 더는 통하지 않는다. 비밀번호를 바꾼
     * 계정은 예전 비밀번호로 받은 토큰도 모두 끊긴다 — 기기마다 새 비밀번호로 다시 로그인해야 한다.
     * 해시가 일부러 느려서 계정마다 한 번씩 확인하는 만큼 기동이 늦어진다(계정 10개).
     */
    fun ensure(users: UserRepository, password: String) {
        require(password.isNotBlank()) { "시연용 계정 비밀번호가 비어 있다" }
        ALL.forEach { account ->
            val stored = users.findCredential(account.id)
            when {
                stored == null -> users.insertIfAbsent(account, PasswordHasher.hash(password))
                !PasswordHasher.verify(password, stored.passwordHash) ->
                    users.resetPassword(account.id, PasswordHasher.hash(password))
            }
        }
    }

    /** 시연용 계정이 하나라도 남아 있는지. 계정을 만들지 않게 띄운 서버가 남은 계정을 알리는 데 쓴다. */
    fun anyExists(users: UserRepository): Boolean = ALL.any { users.find(it.id) != null }

    private fun account(
        index: Int,
        name: String,
        organ: String,
        position: String,
        birthday: String,
        statusMessage: String,
    ) = UserDto(
        id = "test$index",
        name = name,
        organName = organ,
        positionName = position,
        positionSort = POSITION_ORDER.indexOf(position).let { if (it < 0) POSITION_ORDER.size else it },
        email = "test$index@eunilsung.com",
        phoneNumber = "010-1000-" + index.toString().padStart(4, '0'),
        birthday = birthday,
        statusMessage = statusMessage,
    )
}
