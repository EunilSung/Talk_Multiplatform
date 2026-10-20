package com.eunilsung.talk.domain.model

data class User(
    val id: String = "",
    val name: String = "",
    val departmentName: String? = null,
    val positionName: String? = null,
    val nickname: String? = null,
)

/** 서버가 보낸 알림 대화의 보낸 사람인지. id 에 [NOTIFICATION_ID_MARK] 가 들어 있다. */
val User.isNotificationSender: Boolean
    get() = id.contains(NOTIFICATION_ID_MARK)
