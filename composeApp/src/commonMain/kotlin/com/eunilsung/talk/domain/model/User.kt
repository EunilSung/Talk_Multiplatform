package com.eunilsung.talk.domain.model

data class User(
    val id: String = "",
    val name: String = "",
    val departmentName: String? = null,
    val positionName: String? = null,
    val nickname: String? = null,
    val isSelect: Boolean = false,
    val presencePc: String? = null,
    val presenceMobile: String? = null
)
