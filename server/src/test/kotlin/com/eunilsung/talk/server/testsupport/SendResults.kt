package com.eunilsung.talk.server.testsupport

import com.eunilsung.talk.server.repository.InviteResult
import com.eunilsung.talk.server.repository.RoomChange
import com.eunilsung.talk.server.repository.SendResult
import com.eunilsung.talk.shared.api.MessageDto

/** 들어간 대화. 넣지 못했으면 null. */
fun SendResult.sentMessage(): MessageDto? = (this as? SendResult.Sent)?.message

/** 초대한 결과. 초대하지 못했으면 null. */
fun InviteResult.invitedChange(): RoomChange? = (this as? InviteResult.Invited)?.change
