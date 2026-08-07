package com.eunilsung.talk.data.sample

/** 로컬 테스트용 그룹 구성 — 사용자별 그룹 초기 배치. */
object TestGroups {

    /** 기본그룹(미분류) 식별자. */
    const val DEFAULT_GROUP_ID = "0"
    const val DEFAULT_GROUP_ALINE_CODE = "-3"

    data class Seed(
        val id: String,
        val name: String,
        val alineCode: String,
        val memberIds: List<String>,
    )

    val CUSTOM: List<Seed> = listOf(
        Seed("1", "개발팀", "1", listOf("test1", "test2", "test3", "test4")),
        Seed("2", "기획팀", "2", listOf("test5", "test6")),
        Seed("3", "디자인팀", "3", listOf("test7", "test8")),
    )

    /** 사용자 그룹 어디에도 속하지 않는 계정 = 기본그룹 소속. */
    val DEFAULT_GROUP_MEMBER_IDS: List<String>
        get() {
            val assigned = CUSTOM.flatMap { it.memberIds }.toSet()
            return TestAccounts.ALL.map { it.userId }.filterNot { it in assigned }
        }
}
