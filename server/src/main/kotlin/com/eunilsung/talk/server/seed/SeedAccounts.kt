package com.eunilsung.talk.server.seed

import com.eunilsung.talk.server.auth.PasswordHasher
import com.eunilsung.talk.server.repository.UserRepository
import com.eunilsung.talk.shared.api.UserDto

/**
 * 시연용 계정 `test1` ~ `test10`.
 *
 * 회원가입이 없는 쇼케이스라 계정을 서버가 미리 만들어 둔다. 앱의 로컬 모드가 쓰는 계정과
 * 같은 사람들이라, 서버 모드로 바꿔도 같은 아이디로 로그인된다.
 */
object SeedAccounts {

    /** 전 계정 공통 비밀번호. */
    const val PASSWORD = "1234"

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
     * 사용자 표가 비어 있을 때만 채운다.
     *
     * 해시가 일부러 느려서 매번 돌리면 기동이 그만큼 늦어진다. 한 명이라도 있으면 이미 채운 것으로 본다.
     */
    fun ensure(users: UserRepository) {
        if (users.count() > 0) return
        ALL.forEach { users.insertIfAbsent(it, PasswordHasher.hash(PASSWORD)) }
    }

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
