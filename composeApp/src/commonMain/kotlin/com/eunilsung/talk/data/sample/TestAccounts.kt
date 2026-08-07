package com.eunilsung.talk.data.sample

/** 로컬 테스트 계정 목록 (`test1` ~ `test10`). 사용자 정보의 단일 출처. */
object TestAccounts {

    /** 전 계정 공통 비밀번호. */
    const val PASSWORD = "1234"

    // pcStatus: "1" 로그인 / "2" 자리비움 / "3" 업무중 / "4" 기타 / 그 외 로그아웃
    // mobileStatus: "0" 오프라인 / 그 외 온라인
    data class Account(
        val userId: String,
        val userName: String,
        val organName: String,
        val positionName: String,
        val email: String,
        val phoneNumber: String,
        val birthday: String,
        val statusMessage: String,
        val pcStatus: String,
        val mobileStatus: String,
        /** 그룹 내 사용자 정렬 키 — 직위 순(직급이 높을수록 앞). */
        val positionSortCode: String,
    )

    // 앞에 올수록 상위 직급 — [Account.positionSortCode] 산출에 사용. [ALL] 보다 위에 있어야 한다.
    private val POSITION_ORDER = listOf("부장", "차장", "팀장", "과장", "대리", "주임", "사원")

    val ALL: List<Account> = listOf(
        account(1, "김민준", "개발1팀", "팀장", "19850312", "코드 리뷰 중입니다", pc = "1"),
        account(2, "이서연", "개발1팀", "대리", "19920705", "", pc = "1"),
        account(3, "박도윤", "개발2팀", "과장", "19880921", "회의 중", pc = "3"),
        account(4, "최지우", "개발2팀", "사원", "19960214", "", pc = "0"),
        account(5, "정하윤", "기획팀", "차장", "19830618", "기획서 작성 중", pc = "2"),
        account(6, "강시우", "기획팀", "사원", "19970430", "", pc = "1"),
        account(7, "조유진", "디자인팀", "팀장", "19870104", "디자인 검토 요청 주세요", pc = "1"),
        account(8, "윤준서", "디자인팀", "주임", "19941123", "", pc = "0"),
        account(9, "임채원", "경영지원팀", "부장", "19790827", "외근 중", pc = "4"),
        account(10, "한지호", "경영지원팀", "사원", "19980509", "", pc = "0"),
    )

    fun find(userId: String): Account? =
        ALL.firstOrNull { it.userId.equals(userId.trim(), ignoreCase = true) }

    private fun account(
        index: Int,
        name: String,
        organ: String,
        position: String,
        birthday: String,
        statusMessage: String,
        pc: String,
    ) = Account(
        userId = "test$index",
        userName = name,
        organName = organ,
        positionName = position,
        email = "test$index@eunilsung.com",
        phoneNumber = "010-1000-" + index.toString().padStart(4, '0'),
        birthday = birthday,
        statusMessage = statusMessage,
        pcStatus = pc,
        mobileStatus = "1",
        positionSortCode = POSITION_ORDER.indexOf(position)
            .let { if (it < 0) POSITION_ORDER.size else it }
            .toString()
            .padStart(2, '0'),
    )
}
