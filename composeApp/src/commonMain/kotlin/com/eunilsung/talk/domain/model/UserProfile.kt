package com.eunilsung.talk.domain.model

/** 사용자 상세 프로필 — 프로필 시트가 보여주는 전체 정보. */
data class UserProfile(
    val userId: String,
    val name: String,
    val nameEn: String = "",
    val companyId: String = "",
    val position: String = "",
    val department: String = "",
    val localCallNum: String = "",
    val extensionNum: String = "",
    val mobileNum: String = "",
    val faxNum: String = "",
    val email: String = "",
    val motto: String = ""
)
