package com.eunilsung.talk.data.remote.server

import com.eunilsung.talk.Config
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.BallotRequest
import com.eunilsung.talk.shared.api.BookmarkDto
import com.eunilsung.talk.shared.api.BookmarkRequest
import com.eunilsung.talk.shared.api.BookmarksResponse
import com.eunilsung.talk.shared.api.ChatGroupDto
import com.eunilsung.talk.shared.api.ChatGroupsDto
import com.eunilsung.talk.shared.api.ContactGroupDto
import com.eunilsung.talk.shared.api.ContactGroupsDto
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.CreateVoteRequest
import com.eunilsung.talk.shared.api.FileDto
import com.eunilsung.talk.shared.api.InviteRequest
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.MarkReadRequest
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessagesResponse
import com.eunilsung.talk.shared.api.MuteRoomRequest
import com.eunilsung.talk.shared.api.NoticeChangeResponse
import com.eunilsung.talk.shared.api.NoticeDto
import com.eunilsung.talk.shared.api.PinRoomRequest
import com.eunilsung.talk.shared.api.RecallRequest
import com.eunilsung.talk.shared.api.RegisterPushTokenRequest
import com.eunilsung.talk.shared.api.RenameRoomRequest
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.RoomsResponse
import com.eunilsung.talk.shared.api.SendMessageRequest
import com.eunilsung.talk.shared.api.SetNoticeRequest
import com.eunilsung.talk.shared.api.ToggleReactionRequest
import com.eunilsung.talk.shared.api.UnreadCountDto
import com.eunilsung.talk.shared.api.UnreadCountsResponse
import com.eunilsung.talk.shared.api.UserDto
import com.eunilsung.talk.shared.api.UsersResponse
import com.eunilsung.talk.shared.api.VoteChangeResponse
import com.eunilsung.talk.shared.api.VoteDto
import com.eunilsung.talk.shared.api.VotesResponse
import com.eunilsung.talk.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 서버 요청 한 번의 결과.
 *
 * 서버가 거절한 것([Rejected])과 서버에 닿지 못한 것([Unreachable])을 가른다. 앞쪽은 다시 보내도
 * 같은 답이 오고, 뒤쪽은 다시 보내 볼 수 있다. 섞이면 화면이 잘못된 안내를 하게 된다.
 */
sealed interface ServerResult<out T> {
    data class Success<T>(val value: T) : ServerResult<T>

    /** 서버가 답했지만 거절했다. [code] 는 `ApiErrorCode` 값이다. */
    data class Rejected(val status: Int, val code: String) : ServerResult<Nothing>

    /** 응답이 없거나 읽을 수 없었다 — 네트워크 끊김·타임아웃·서버 다운. */
    data object Unreachable : ServerResult<Nothing>
}

/** 성공했으면 그 값, 아니면 null. 실패 이유를 가릴 필요가 없는 자리에 쓴다. */
fun <T> ServerResult<T>.valueOrNull(): T? = (this as? ServerResult.Success)?.value

/**
 * Talk 서버와 주고받는 것들.
 *
 * 인터페이스로 둔 이유는 테스트다. 실제 호출을 대신할 대역을 끼울 수 있어야 저장소 로직을
 * 네트워크 없이 검증할 수 있다.
 */
interface TalkServer {
    suspend fun login(id: String, password: String): ServerResult<LoginResponse>

    /** [token] 을 서버에서 지운다. 실패해도 앱은 로그아웃된 것으로 본다. */
    suspend fun logout(token: String)

    /** 지금 토큰의 주인. 토큰이 아직 유효한지 확인하는 데도 쓴다. */
    suspend fun me(): ServerResult<UserDto>

    /** 이 기기의 푸시 토큰을 지금 로그인에 묶어 등록한다. 로그아웃하면 서버에서 함께 지워진다. */
    suspend fun registerPushToken(token: String, platform: String): ServerResult<Unit>

    /** 전체 사용자. 접속 여부가 함께 온다. */
    suspend fun users(): ServerResult<List<UserDto>>

    suspend fun user(userId: String): ServerResult<UserDto>

    /** 내그룹 전부. */
    suspend fun contactGroups(): ServerResult<List<ContactGroupDto>>

    /** 내그룹을 [groups] 로 통째로 바꾼다. */
    suspend fun putContactGroups(groups: List<ContactGroupDto>): ServerResult<List<ContactGroupDto>>

    /** 내가 참여 중인 방 전부. */
    suspend fun rooms(): ServerResult<List<RoomDto>>

    suspend fun room(roomId: String): ServerResult<RoomDto>

    /** 방을 만든다. 상대가 한 명이고 이미 방이 있으면 그 방이 온다. */
    suspend fun createRoom(memberIds: List<String>): ServerResult<RoomDto>

    suspend fun invite(roomId: String, userIds: List<String>): ServerResult<RoomDto>

    suspend fun leaveRoom(roomId: String): ServerResult<Unit>

    suspend fun renameRoom(roomId: String, title: String): ServerResult<Unit>

    suspend fun muteRoom(roomId: String, isMuted: Boolean): ServerResult<Unit>

    /**
     * 대화 한 쪽. [afterId] 를 주면 그 뒤를 앞에서부터, [beforeId] 를 주면 그 앞을 뒤에서부터,
     * 둘 다 없으면 가장 최근 것을 받는다.
     */
    suspend fun messages(
        roomId: String,
        afterId: String? = null,
        beforeId: String? = null,
        limit: Int,
    ): ServerResult<List<MessageDto>>

    /** 대화를 보낸다. 같은 [SendMessageRequest.id] 로 다시 보내도 서버에는 한 번만 들어간다. */
    suspend fun sendMessage(roomId: String, request: SendMessageRequest): ServerResult<MessageDto>

    suspend fun markRead(roomId: String, messageId: String): ServerResult<Unit>

    /** [fromId] 부터 뒤쪽 대화들의 안읽음 수. */
    suspend fun unreadCounts(roomId: String, fromId: String): ServerResult<List<UnreadCountDto>>

    /** 공감을 누른다. 켤지 끌지는 서버가 정하고, 바뀐 대화를 돌려준다. */
    suspend fun toggleReaction(roomId: String, messageId: String, kind: String): ServerResult<MessageDto>

    /** 내가 보낸 대화를 회수한다. 바뀐 대화를 돌려준다. */
    suspend fun recallMessage(roomId: String, messageId: String): ServerResult<MessageDto>

    /** 방의 공지. 공지가 없으면 내용이 빈 값이 온다. */
    suspend fun notice(roomId: String): ServerResult<NoticeDto>

    suspend fun setNotice(roomId: String, content: String): ServerResult<NoticeChangeResponse>

    suspend fun deleteNotice(roomId: String): ServerResult<NoticeChangeResponse>

    /** 이 방에서 내가 꽂은 책갈피. */
    suspend fun bookmarks(roomId: String): ServerResult<List<BookmarkDto>>

    suspend fun addBookmark(roomId: String, messageId: String): ServerResult<Unit>

    suspend fun removeBookmark(roomId: String, messageId: String): ServerResult<Unit>

    /** 방에 파일을 올린다. 받은 id 를 대화에 실어 보내야 상대에게 보인다. */
    suspend fun uploadFile(roomId: String, fileName: String, bytes: ByteArray): ServerResult<FileDto>

    /** 파일 바이트를 받는다. 그 방의 참여자만 받을 수 있다. */
    suspend fun downloadFile(fileId: String): ServerResult<ByteArray>

    /** 방을 상단에 고정하거나 푼다. 나에게만 적용된다. */
    suspend fun pinRoom(roomId: String, isPinned: Boolean): ServerResult<Unit>

    /** 내 대화그룹(대화함 위쪽의 칩) 전부. */
    suspend fun chatGroups(): ServerResult<List<ChatGroupDto>>

    /** 내 대화그룹을 [groups] 로 통째로 바꾼다. */
    suspend fun putChatGroups(groups: List<ChatGroupDto>): ServerResult<List<ChatGroupDto>>

    /** 방의 투표 전부, 최근 것부터. */
    suspend fun votes(roomId: String): ServerResult<List<VoteDto>>

    suspend fun vote(roomId: String, voteId: String): ServerResult<VoteDto>

    suspend fun createVote(roomId: String, request: CreateVoteRequest): ServerResult<VoteChangeResponse>

    /** 내 표를 [selectedIdx] 로 바꾼다. 비우면 표를 거둔다. */
    suspend fun castVote(roomId: String, voteId: String, selectedIdx: List<Int>): ServerResult<VoteDto>

    /** 투표를 끝낸다. 만든 사람만 할 수 있다. */
    suspend fun closeVote(roomId: String, voteId: String): ServerResult<VoteChangeResponse>
}

class TalkServerClient(
    private val httpClient: HttpClient,
    private val tokenStore: AuthTokenStore,
    private val baseUrl: String = Config.Server.BASE_URL,
) : TalkServer {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun login(id: String, password: String): ServerResult<LoginResponse> =
        request("로그인", LoginResponse.serializer()) {
            httpClient.post("$baseUrl/auth/login") {
                jsonBody(LoginRequest.serializer(), LoginRequest(id, password))
            }
        }

    override suspend fun logout(token: String) {
        if (token.isBlank()) return
        send("로그아웃") {
            httpClient.post("$baseUrl/auth/logout") { header(HttpHeaders.Authorization, "Bearer $token") }
        }
    }

    override suspend fun me(): ServerResult<UserDto> =
        request("내 정보", UserDto.serializer()) {
            httpClient.get("$baseUrl/users/me") { auth() }
        }

    override suspend fun registerPushToken(token: String, platform: String): ServerResult<Unit> =
        command("푸시 토큰 등록") {
            httpClient.post("$baseUrl/push/token") {
                auth()
                jsonBody(RegisterPushTokenRequest.serializer(), RegisterPushTokenRequest(token, platform))
            }
        }

    override suspend fun users(): ServerResult<List<UserDto>> =
        request("사용자 목록", UsersResponse.serializer()) {
            httpClient.get("$baseUrl/users") { auth() }
        }.map { it.users }

    override suspend fun user(userId: String): ServerResult<UserDto> =
        request("프로필", UserDto.serializer()) {
            httpClient.get("$baseUrl/users/${userId.encodeURLPathPart()}") { auth() }
        }

    override suspend fun contactGroups(): ServerResult<List<ContactGroupDto>> =
        request("내그룹 조회", ContactGroupsDto.serializer()) {
            httpClient.get("$baseUrl/contact-groups") { auth() }
        }.map { it.groups }

    override suspend fun putContactGroups(groups: List<ContactGroupDto>): ServerResult<List<ContactGroupDto>> =
        request("내그룹 저장", ContactGroupsDto.serializer()) {
            httpClient.put("$baseUrl/contact-groups") {
                auth()
                jsonBody(ContactGroupsDto.serializer(), ContactGroupsDto(groups))
            }
        }.map { it.groups }

    override suspend fun rooms(): ServerResult<List<RoomDto>> =
        request("방 목록", RoomsResponse.serializer()) {
            httpClient.get("$baseUrl/rooms") { auth() }
        }.map { it.rooms }

    override suspend fun room(roomId: String): ServerResult<RoomDto> =
        request("방 정보", RoomDto.serializer()) {
            httpClient.get(roomUrl(roomId)) { auth() }
        }

    override suspend fun createRoom(memberIds: List<String>): ServerResult<RoomDto> =
        request("방 만들기", RoomDto.serializer()) {
            httpClient.post("$baseUrl/rooms") {
                auth()
                jsonBody(CreateRoomRequest.serializer(), CreateRoomRequest(memberIds))
            }
        }

    override suspend fun invite(roomId: String, userIds: List<String>): ServerResult<RoomDto> =
        request("초대", RoomDto.serializer()) {
            httpClient.post("${roomUrl(roomId)}/members") {
                auth()
                jsonBody(InviteRequest.serializer(), InviteRequest(userIds))
            }
        }

    override suspend fun leaveRoom(roomId: String): ServerResult<Unit> =
        command("방 나가기") {
            httpClient.post("${roomUrl(roomId)}/leave") { auth() }
        }

    override suspend fun renameRoom(roomId: String, title: String): ServerResult<Unit> =
        command("방 이름 변경") {
            httpClient.put("${roomUrl(roomId)}/title") {
                auth()
                jsonBody(RenameRoomRequest.serializer(), RenameRoomRequest(title))
            }
        }

    override suspend fun muteRoom(roomId: String, isMuted: Boolean): ServerResult<Unit> =
        command("방 알림 설정") {
            httpClient.put("${roomUrl(roomId)}/mute") {
                auth()
                jsonBody(MuteRoomRequest.serializer(), MuteRoomRequest(isMuted))
            }
        }

    override suspend fun messages(
        roomId: String,
        afterId: String?,
        beforeId: String?,
        limit: Int,
    ): ServerResult<List<MessageDto>> =
        request("대화 받기", MessagesResponse.serializer()) {
            httpClient.get("${roomUrl(roomId)}/messages") {
                auth()
                afterId?.let { parameter("after", it) }
                beforeId?.let { parameter("before", it) }
                parameter("limit", limit)
            }
        }.map { it.messages }

    override suspend fun sendMessage(roomId: String, request: SendMessageRequest): ServerResult<MessageDto> =
        request("대화 보내기", MessageDto.serializer()) {
            httpClient.post("${roomUrl(roomId)}/messages") {
                auth()
                jsonBody(SendMessageRequest.serializer(), request)
            }
        }

    override suspend fun markRead(roomId: String, messageId: String): ServerResult<Unit> =
        command("읽음") {
            httpClient.post("${roomUrl(roomId)}/read") {
                auth()
                jsonBody(MarkReadRequest.serializer(), MarkReadRequest(messageId))
            }
        }

    override suspend fun unreadCounts(roomId: String, fromId: String): ServerResult<List<UnreadCountDto>> =
        request("안읽음 수", UnreadCountsResponse.serializer()) {
            httpClient.get("${roomUrl(roomId)}/unread") {
                auth()
                parameter("from", fromId)
            }
        }.map { it.counts }

    override suspend fun toggleReaction(roomId: String, messageId: String, kind: String): ServerResult<MessageDto> =
        request("공감", MessageDto.serializer()) {
            httpClient.post("${roomUrl(roomId)}/reactions") {
                auth()
                jsonBody(ToggleReactionRequest.serializer(), ToggleReactionRequest(messageId, kind))
            }
        }

    override suspend fun recallMessage(roomId: String, messageId: String): ServerResult<MessageDto> =
        request("회수", MessageDto.serializer()) {
            httpClient.post("${roomUrl(roomId)}/recall") {
                auth()
                jsonBody(RecallRequest.serializer(), RecallRequest(messageId))
            }
        }

    override suspend fun notice(roomId: String): ServerResult<NoticeDto> =
        request("공지 조회", NoticeDto.serializer()) {
            httpClient.get("${roomUrl(roomId)}/notice") { auth() }
        }

    override suspend fun setNotice(roomId: String, content: String): ServerResult<NoticeChangeResponse> =
        request("공지 등록", NoticeChangeResponse.serializer()) {
            httpClient.put("${roomUrl(roomId)}/notice") {
                auth()
                jsonBody(SetNoticeRequest.serializer(), SetNoticeRequest(content))
            }
        }

    override suspend fun deleteNotice(roomId: String): ServerResult<NoticeChangeResponse> =
        request("공지 삭제", NoticeChangeResponse.serializer()) {
            httpClient.delete("${roomUrl(roomId)}/notice") { auth() }
        }

    override suspend fun bookmarks(roomId: String): ServerResult<List<BookmarkDto>> =
        request("책갈피 조회", BookmarksResponse.serializer()) {
            httpClient.get("${roomUrl(roomId)}/bookmarks") { auth() }
        }.map { it.bookmarks }

    override suspend fun addBookmark(roomId: String, messageId: String): ServerResult<Unit> =
        command("책갈피 등록") {
            httpClient.put("${roomUrl(roomId)}/bookmarks") {
                auth()
                jsonBody(BookmarkRequest.serializer(), BookmarkRequest(messageId))
            }
        }

    override suspend fun removeBookmark(roomId: String, messageId: String): ServerResult<Unit> =
        command("책갈피 해제") {
            httpClient.delete("${roomUrl(roomId)}/bookmarks") {
                auth()
                parameter("messageId", messageId)
            }
        }

    override suspend fun uploadFile(roomId: String, fileName: String, bytes: ByteArray): ServerResult<FileDto> =
        request("파일 올리기", FileDto.serializer(), FILE_TIMEOUT_MS) {
            httpClient.post("${roomUrl(roomId)}/files") {
                auth()
                parameter("name", fileName)
                contentType(ContentType.Application.OctetStream)
                setBody(bytes)
            }
        }

    override suspend fun downloadFile(fileId: String): ServerResult<ByteArray> {
        val response = runCatching {
            withTimeoutOrNull(FILE_TIMEOUT_MS) {
                val response = httpClient.get("$baseUrl/files/${fileId.encodeURLPathPart()}") { auth() }
                response to if (response.status.isSuccess()) response.readRawBytes() else ByteArray(0)
            }
        }.getOrNull() ?: return ServerResult.Unreachable
        return if (response.first.status.isSuccess()) {
            ServerResult.Success(response.second)
        } else {
            rejected("파일 받기", response.first, "")
        }
    }

    override suspend fun pinRoom(roomId: String, isPinned: Boolean): ServerResult<Unit> =
        command("방 고정") {
            httpClient.put("${roomUrl(roomId)}/pin") {
                auth()
                jsonBody(PinRoomRequest.serializer(), PinRoomRequest(isPinned))
            }
        }

    override suspend fun chatGroups(): ServerResult<List<ChatGroupDto>> =
        request("대화그룹 조회", ChatGroupsDto.serializer()) {
            httpClient.get("$baseUrl/chat-groups") { auth() }
        }.map { it.groups }

    override suspend fun putChatGroups(groups: List<ChatGroupDto>): ServerResult<List<ChatGroupDto>> =
        request("대화그룹 저장", ChatGroupsDto.serializer()) {
            httpClient.put("$baseUrl/chat-groups") {
                auth()
                jsonBody(ChatGroupsDto.serializer(), ChatGroupsDto(groups))
            }
        }.map { it.groups }

    override suspend fun votes(roomId: String): ServerResult<List<VoteDto>> =
        request("투표 목록", VotesResponse.serializer()) {
            httpClient.get("${roomUrl(roomId)}/votes") { auth() }
        }.map { it.votes }

    override suspend fun vote(roomId: String, voteId: String): ServerResult<VoteDto> =
        request("투표 조회", VoteDto.serializer()) {
            httpClient.get(voteUrl(roomId, voteId)) { auth() }
        }

    override suspend fun createVote(roomId: String, request: CreateVoteRequest): ServerResult<VoteChangeResponse> =
        request("투표 만들기", VoteChangeResponse.serializer()) {
            httpClient.post("${roomUrl(roomId)}/votes") {
                auth()
                jsonBody(CreateVoteRequest.serializer(), request)
            }
        }

    override suspend fun castVote(roomId: String, voteId: String, selectedIdx: List<Int>): ServerResult<VoteDto> =
        request("투표하기", VoteDto.serializer()) {
            httpClient.put("${voteUrl(roomId, voteId)}/ballot") {
                auth()
                jsonBody(BallotRequest.serializer(), BallotRequest(selectedIdx))
            }
        }

    override suspend fun closeVote(roomId: String, voteId: String): ServerResult<VoteChangeResponse> =
        request("투표 종료", VoteChangeResponse.serializer()) {
            httpClient.post("${voteUrl(roomId, voteId)}/close") { auth() }
        }

    private fun roomUrl(roomId: String): String = "$baseUrl/rooms/${roomId.encodeURLPathPart()}"

    private fun voteUrl(roomId: String, voteId: String): String =
        "${roomUrl(roomId)}/votes/${voteId.encodeURLPathPart()}"

    /**
     * 인증 헤더.
     *
     * 공용 [httpClient] 에 기본 헤더로 박지 않는다. 그 클라이언트는 이미지 로딩과 링크 미리보기도
     * 함께 쓰므로, 박아 두면 남의 서버에까지 우리 토큰이 실려 나간다.
     */
    private fun HttpRequestBuilder.auth() {
        val token = tokenStore.token()
        if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun <T> HttpRequestBuilder.jsonBody(serializer: KSerializer<T>, body: T) {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(serializer, body))
    }

    /** 본문이 있는 응답을 읽는다. */
    private suspend fun <T> request(
        label: String,
        serializer: KSerializer<T>,
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
        call: suspend () -> HttpResponse,
    ): ServerResult<T> {
        val (response, text) = send(label, timeoutMs, call) ?: return ServerResult.Unreachable
        if (!response.status.isSuccess()) return rejected(label, response, text)
        val value = runCatching { json.decodeFromString(serializer, text) }.getOrNull()
        if (value == null) {
            Log.message("[Server] $label 응답을 읽지 못했다")
            return ServerResult.Unreachable
        }
        return ServerResult.Success(value)
    }

    /** 본문 없이 성공 여부만 돌아오는 요청. */
    private suspend fun command(label: String, call: suspend () -> HttpResponse): ServerResult<Unit> {
        val (response, text) = send(label, call = call) ?: return ServerResult.Unreachable
        return if (response.status.isSuccess()) ServerResult.Success(Unit) else rejected(label, response, text)
    }

    /** 요청을 보내고 상태와 본문을 받는다. 닿지 못하면 null. */
    private suspend fun send(
        label: String,
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
        call: suspend () -> HttpResponse,
    ): Pair<HttpResponse, String>? =
        runCatching {
            withTimeoutOrNull(timeoutMs) {
                val response = call()
                response to response.bodyAsText()
            }
        }.getOrElse {
            Log.message("[Server] $label 실패 — ${it.message}")
            null
        } ?: run {
            Log.message("[Server] $label 응답 없음 — $baseUrl")
            null
        }

    private fun rejected(label: String, response: HttpResponse, text: String): ServerResult.Rejected {
        val code = runCatching { json.decodeFromString(ApiError.serializer(), text).code }.getOrDefault("")
        Log.message("[Server] $label 거절 — ${response.status.value} $code")
        return ServerResult.Rejected(response.status.value, code)
    }

    private fun <T, R> ServerResult<T>.map(transform: (T) -> R): ServerResult<R> = when (this) {
        is ServerResult.Success -> ServerResult.Success(transform(value))
        is ServerResult.Rejected -> this
        ServerResult.Unreachable -> ServerResult.Unreachable
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
        /** 파일은 크기만큼 오래 걸린다. 일반 요청과 같은 제한을 걸면 큰 파일이 늘 끊긴다. */
        const val FILE_TIMEOUT_MS = 120_000L
    }
}
