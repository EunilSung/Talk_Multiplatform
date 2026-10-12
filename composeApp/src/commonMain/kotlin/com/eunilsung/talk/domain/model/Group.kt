package com.eunilsung.talk.domain.model

sealed class Group {
    data class Item (
        var id : String,
        var name : String,
        var alineCode : String,
        var userData : List<User> = emptyList(),
        var isExpanded: Boolean = false                             
    )

    data class User(
        val startOffset: String? = null,
        val groupId: String? = null,
        val userId: String? = null,
        val alias: String? = null,
        val isAlarm: String? = null,
        val userName: String? = null,
        val pcStatus: String? = null,
        val phoneNumber: String? = null,
        val email: String? = null,
        val birthday: String? = null,
        val solarLunar: String? = null,
        val departmentName: String? = null,
        val positionName: String? = null,
        val responsibilities: String? = null,
        val statusMessage: String? = null,
        val positionCode: String? = null,
        val positionSortCode: String? = null,
        val employeeNumber: String? = null,
        val etcStatus: String? = null,
        val userType: String? = null,
        val departmentTopCode: String? = null,
        val offset21: String? = null,
        val offset22: String? = null,
        val offset23: String? = null,
        val offset24: String? = null,
        val offset25: String? = null,
        val offset26: String? = null,
        val pcMobileStatus: String? = null,
        val mobileStatus: String? = null
    )
}

/** 기본그룹(어느 그룹에도 넣지 않은 사람들)의 고정 id 와 정렬 코드. 화면이 이 값으로 기본그룹을 알아본다. */
const val DEFAULT_GROUP_ID = "0"
const val DEFAULT_GROUP_ALINE_CODE = "-3"

/** 기본그룹(서버 미분류 사용자) 여부 — [DEFAULT_GROUP_ID] + [DEFAULT_GROUP_ALINE_CODE]. */
val Group.Item.isDefaultGroup: Boolean
    get() = id == DEFAULT_GROUP_ID && alineCode == DEFAULT_GROUP_ALINE_CODE

/** 내 프로필 그룹의 고정 id — 목록 최상단에 오는 특수 그룹. */
const val MY_PROFILE_GROUP_ID = "myprofile"

/** 내 프로필 그룹인지 — 이름 표기·메뉴 제한 분기에 쓴다. */
val Group.Item.isMyProfileGroup: Boolean get() = id == MY_PROFILE_GROUP_ID
