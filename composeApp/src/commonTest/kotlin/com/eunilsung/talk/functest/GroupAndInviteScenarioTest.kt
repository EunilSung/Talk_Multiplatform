package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.repository.AddUserResult
import kotlinx.coroutines.flow.first
import kotlin.test.Test

/** 대화그룹·사람·초대 — 기기에서 바꾼 것이 서버에 남고, 서버의 사람 정보가 그대로 내려오는지. */
class GroupAndInviteScenarioTest {

    @Test
    fun 대화그룹() = functionalSuite("대화그룹") { harness ->
        listOf(
            scenario("만들고담고지우기") {
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                harness.rooms.fetchChatRooms()

                suspend fun mine() = harness.rooms.getChatGroups().first().firstOrNull { it.name.startsWith(tag) }
                suspend fun onServer() = harness.server.chatGroups().must("대화그룹 조회").firstOrNull { it.name.startsWith(tag) }

                step("만든 대화그룹이 서버에 남는다") {
                    harness.rooms.createChatGroup(tag)
                    require(mine() != null, "만든 그룹이 목록에 없다")
                    require(onServer() != null, "서버에 그룹이 없다")
                }
                step("방을 담으면 서버의 그룹에도 그 방이 들어 있다") {
                    harness.rooms.addRoomToGroup(mine()!!.id, roomId)
                    require(roomId in mine()!!.roomIds, "목록의 그룹에 방이 없다")
                    require(roomId in onServer()!!.roomIds, "서버의 그룹에 방이 없다")
                }
                step("이름을 바꾸면 서버의 이름도 바뀐다") {
                    harness.rooms.renameChatGroup(mine()!!.id, "$tag-새이름")
                    requireEquals("$tag-새이름", onServer()?.name, "서버의 그룹 이름")
                }
                step("방을 빼고 그룹을 지우면 서버에서도 사라진다") {
                    harness.rooms.removeRoomFromGroup(mine()!!.id, roomId)
                    require(roomId !in onServer()!!.roomIds, "뺀 방이 서버의 그룹에 남아 있다")
                    harness.rooms.deleteChatGroup(mine()!!.id)
                    require(onServer() == null, "지운 그룹이 서버에 남아 있다")
                }
            },
        )
    }

    @Test
    fun 사람() = functionalSuite("사람") { harness ->
        listOf(
            scenario("사용자목록과프로필") {
                step("서버의 사용자 목록에 테스트 계정과 AI 가 있다") {
                    val ids = harness.server.users().must("사용자 목록").map { it.id }.toSet()
                    require(FunctionalTestRunner.ALLOWED_ACCOUNTS.all { it in ids }, "테스트 계정이 빠졌다: $ids")
                    require(RealServerHarness.AI in ids, "AI 계정이 없다")
                }
                step("그룹 화면에 사람이 채워진다") {
                    harness.groups.fetchGroups()
                    val people = harness.groups.getGroups().first().flatMap { it.userData }.mapNotNull { it.userId }.toSet()
                    require(RealServerHarness.PEER in people, "그룹 화면에 상대가 없다: $people")
                }
                step("다른 사람의 프로필을 받아 온다") {
                    val profile = harness.profiles.fetchProfile(RealServerHarness.PEER).getOrNull()
                    requireEquals(RealServerHarness.PEER, profile?.userId, "프로필의 아이디")
                    require(profile?.name?.isNotBlank() == true, "프로필의 이름이 비어 있다")
                    requireEquals(profile, harness.profiles.getCachedProfile(RealServerHarness.PEER), "기억해 둔 프로필")
                }
            },
            scenario("연락처그룹") {
                val tag = runTag()

                suspend fun mine() = harness.groups.getGroups().first().firstOrNull { it.name.startsWith(tag) }
                suspend fun onServer() = harness.server.contactGroups().must("연락처 그룹 조회").firstOrNull { it.name.startsWith(tag) }

                step("만든 그룹이 서버에 남는다") {
                    harness.groups.fetchGroups()
                    require(harness.groups.createGroup(tag), "그룹 만들기가 실패했다")
                    require(mine() != null, "만든 그룹이 화면에 없다")
                    require(onServer() != null, "서버에 그룹이 없다")
                }
                step("사람을 담으면 서버의 그룹에도 들어가고 두 번 담기지 않는다") {
                    val groupId = mine()!!.id
                    requireEquals(AddUserResult.SUCCESS, harness.groups.copyUserToGroup(RealServerHarness.PEER, groupId), "담기 결과")
                    requireEquals(listOf(RealServerHarness.PEER), onServer()?.memberIds, "서버의 그룹 구성원")
                    requireEquals(AddUserResult.ALREADY_EXISTS, harness.groups.copyUserToGroup(RealServerHarness.PEER, groupId), "다시 담기 결과")
                }
                step("사람을 빼고 그룹을 지우면 서버에서도 사라진다") {
                    val groupId = mine()!!.id
                    require(harness.groups.removeUserFromGroup(RealServerHarness.PEER, groupId), "빼기가 실패했다")
                    requireEquals(emptyList<String>(), onServer()?.memberIds, "뺀 뒤 서버의 구성원")
                    require(harness.groups.deleteGroup(groupId), "그룹 지우기가 실패했다")
                    require(onServer() == null, "지운 그룹이 서버에 남아 있다")
                }
            },
        )
    }

    @Test
    fun 초대() = functionalSuite("초대") { harness ->
        listOf(
            scenario("방만들기와나중초대") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                var roomId = ""

                step("사람을 골라 방을 만들면 목록에 생기고 초대 알림이 남는다") {
                    roomId = harness.invite.inviteUsers(
                        chatRoomId = "",
                        invitedUsers = listOf(RealServerHarness.PEER to peer.name, RealServerHarness.THIRD to "셋째"),
                        existingUserCount = 1,
                    ).orEmpty()
                    require(roomId.isNotBlank(), "방이 만들어지지 않았다")
                    harness.track(roomId)
                    require(harness.room(roomId) != null, "만든 방이 목록에 없다")
                    harness.chats.fetchChats(roomId)
                    require(harness.chatsOf(roomId).any { it.chatType == Chat.Type.INVITE }, "초대 알림 대화가 없다")
                }
                step("나중에 초대받은 사람은 그 전 대화를 보지 못한다") {
                    peer.say(roomId, "$tag 초대 전의 말")
                    val invited = harness.invite.inviteUsers(roomId, listOf(LATECOMER to "늦게 온 사람"), existingUserCount = 3)
                    requireEquals(roomId, invited, "초대한 방")
                    peer.say(roomId, "$tag 초대 뒤의 말")
                    val late = harness.peer(LATECOMER)
                    val seen = late.messages(roomId).map { it.content }
                    require("$tag 초대 뒤의 말" in seen, "초대 뒤의 대화가 안 보인다: $seen")
                    require("$tag 초대 전의 말" !in seen, "초대 전의 대화가 보인다")
                }
                step("참여자 수가 늘고 방에서 본 참여자에도 들어 있다") {
                    harness.rooms.fetchChatRooms()
                    requireEquals(4, harness.room(roomId)?.displayUserCount, "참여자 수")
                    require(LATECOMER in harness.chats.fetchChatRoomUsers(roomId).map { it.id }, "새 참여자가 목록에 없다")
                }
                step("상대가 나가면 알림으로 참여자 수가 줄고 퇴장 알림이 남는다") {
                    peer.client.leaveRoom(roomId).must("상대 나가기")
                    awaitTrue("참여자 수 감소") { harness.room(roomId)?.displayUserCount == 3 }
                    awaitTrue("퇴장 알림 도착") { harness.chatsOf(roomId).any { it.chatType == Chat.Type.EXIT } }
                }
            },
        )
    }

    private companion object {
        const val LATECOMER = "test4"
    }
}
