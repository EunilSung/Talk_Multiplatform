package com.eunilsung.talk.functest

import com.eunilsung.talk.data.mapper.ChatMapper
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.data.mapper.GroupMapper
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.remote.server.AuthTokenStore
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServerClient
import com.eunilsung.talk.data.remote.server.TalkSocket
import com.eunilsung.talk.data.remote.server.UserDirectory
import com.eunilsung.talk.data.remote.server.valueOrNull
import com.eunilsung.talk.data.repository.ChatRoomListRepositoryImpl
import com.eunilsung.talk.data.repository.ChatRoomRepositoryImpl
import com.eunilsung.talk.data.repository.GroupRepositoryImpl
import com.eunilsung.talk.data.repository.GroupRepositoryImpl.Companion.toGroupUser
import com.eunilsung.talk.data.repository.InviteRepositoryImpl
import com.eunilsung.talk.data.repository.LoginRepositoryImpl
import com.eunilsung.talk.data.repository.UserProfileRepositoryImpl
import com.eunilsung.talk.data.repository.VoteRepositoryImpl
import com.eunilsung.talk.data.sample.ChatSenderOverride
import com.eunilsung.talk.data.sample.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.data.sample.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.data.sample.LocalGroupRepositoryImpl
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import com.eunilsung.talk.testsupport.FakeFileMetadataResolver
import com.eunilsung.talk.testsupport.TestLocalSecret
import com.eunilsung.talk.testsupport.TestMyInfo
import com.eunilsung.talk.testsupport.createTestDatabase
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first

/**
 * 기능 테스트를 **진짜 서버에 붙여** 돌리기 위한 준비 도구.
 *
 * ### 무엇이 진짜이고 무엇이 대역인가
 *
 * 서버와 주고받는 것은 전부 앱이 실제로 쓰는 코드다 — [TalkServerClient](REST), [TalkSocket](WebSocket),
 * 그리고 그 위의 저장소 구현들. 다른 테스트는 이 자리에 가짜 서버를 끼우기 때문에, 앱과 서버가
 * 계약을 다르게 알고 있어도 양쪽 테스트가 모두 통과할 수 있다. 여기서만 그 어긋남이 드러난다.
 *
 * 대역은 기기에 묶인 것뿐이다. 기기 DB 는 메모리 DB, 설정은 메모리 설정, 기기의 파일은 메모리 파일.
 *
 * ### 기본은 꺼져 있다
 *
 * 서버 주소(`TALK_FUNCTEST_URL`)가 없으면 [startOrNull] 이 null 을 돌려주고 시나리오는 아무 일도 하지
 * 않고 통과한다. **평소 테스트에서 서버로 요청이 나가면 안 되기 때문이다.**
 *
 * ### 상대는 서버 호출로만 움직인다
 *
 * 내 정보(`Config.MyInfo`)는 프로세스에 하나라 한 테스트 안에서 앱을 둘 세울 수 없다. 그래서 나는
 * 앱 전체로, 상대([Peer])는 서버 호출만으로 움직인다. 상대가 한 일이 내 앱에 알림으로 도착해
 * 화면이 보는 자리(기기 DB)에 반영되는지를 본다.
 */
class RealServerHarness private constructor(private val baseUrl: String) {

    private val httpClient: HttpClient = functionalTestHttpClient()
    private val settings = MapSettings()
    private val tokenStore = AuthTokenStore(settings)
    private val database = createTestDatabase()
    private val chatRoomMapper = ChatRoomMapper()
    private val groupMapper = GroupMapper()
    private val peers = mutableListOf<Peer>()
    private val createdRoomIds = mutableListOf<String>()

    /** 기기에 있는 파일. [FakeFileMetadataResolver.put] 으로 올릴 파일을 만들어 둔다. */
    val deviceFiles = FakeFileMetadataResolver()
    val server = TalkServerClient(httpClient, tokenStore, baseUrl)
    val directory = UserDirectory()
    val login = LoginRepositoryImpl(settings, server, tokenStore)

    private val events = TalkSocket(httpClient, tokenStore, baseUrl)
    private val fileStore = ServerFileStore(MapSettings(), server, deviceFiles)
    private val mapper = ServerChatMapper(fileStore)
    private val localRooms = LocalChatRoomListRepositoryImpl(
        login, MapSettings(), chatRoomMapper, database, seedsSampleRooms = false,
    )
    private val localChats = LocalChatRoomRepositoryImpl(
        chatMapper = ChatMapper(),
        chatRoomMapper = chatRoomMapper,
        chatRoomListRepository = localRooms,
        fileMetadataResolver = deviceFiles,
        senderOverride = ChatSenderOverride(),
        database = database,
        seedsSampleChats = false,
    )

    val rooms = ChatRoomListRepositoryImpl(localRooms, server, events, login, mapper)
    val chats = ChatRoomRepositoryImpl(localChats, server, events, mapper, fileStore, deviceFiles, directory, database)
    val invite = InviteRepositoryImpl(server, rooms)
    val votes = VoteRepositoryImpl(server, localChats, mapper)
    val groups = GroupRepositoryImpl(
        LocalGroupRepositoryImpl(
            groupMapper, login, MapSettings(), database,
            seedsSampleGroups = false,
            findUser = { userId, groupId -> directory.find(userId)?.toGroupUser(groupId) },
        ),
        server, directory, groupMapper,
    )
    val profiles = UserProfileRepositoryImpl(server, directory)

    /** 테스트 계정으로 로그인한다. 성공하면 대화방 저장소가 서버 알림을 듣기 시작한다. */
    suspend fun signIn(userId: String = ME, password: String = PASSWORD): Login.LoginResult =
        login.requestLogin(Login.LoginRequest(userId, password))

    /** 다른 테스트 계정으로 로그인한 상대. 서버 호출만 한다. */
    suspend fun peer(userId: String): Peer {
        val peerTokens = AuthTokenStore(MapSettings())
        val client = TalkServerClient(httpClient, peerTokens, baseUrl)
        val session = client.login(userId, PASSWORD).must("상대 로그인 $userId")
        peerTokens.save(session.token)
        return Peer(session.user.id, session.user.name, client, session.token).also { peers += it }
    }

    /**
     * 이 테스트만 쓰는 새 단체방을 만든다. 끝나면 [close] 가 나가 준다.
     *
     * 둘만의 방은 쓰지 않는다. 같은 상대와의 방은 서버가 새로 만들지 않고 있던 방을 돌려주므로,
     * 지난 실행의 대화가 섞여 "첫 대화", "안읽음 1" 같은 판정을 흐린다.
     */
    suspend fun newRoom(vararg memberIds: String): String {
        val roomId = server.createRoom(memberIds.toList()).must("방 만들기").id
        createdRoomIds += roomId
        rooms.fetchChatRooms()
        return roomId
    }

    /** 다른 길(초대 저장소)로 만든 방도 끝날 때 정리되게 올려 둔다. */
    fun track(roomId: String) {
        createdRoomIds += roomId
    }

    suspend fun room(roomId: String): ChatRoom.Item? = rooms.getChatRooms().first().firstOrNull { it.id == roomId }

    suspend fun chatsOf(roomId: String): List<Chat.Item> = chats.getChats(roomId).first()

    /**
     * 만든 방에서 모두 나가고, 이름표(`FT-`)가 붙은 그룹을 지우고, 로그아웃한다.
     * 시나리오가 중간에 깨져도 지난 실행의 흔적이 계정에 쌓이지 않게 한다.
     */
    suspend fun close() {
        createdRoomIds.forEach { roomId ->
            peers.forEach { runCatching { it.client.leaveRoom(roomId) } }
            runCatching { server.leaveRoom(roomId) }
        }
        runCatching {
            val chatGroups = server.chatGroups().valueOrNull().orEmpty()
            if (chatGroups.any { it.name.startsWith(TAG_PREFIX) }) {
                server.putChatGroups(chatGroups.filterNot { it.name.startsWith(TAG_PREFIX) })
            }
            val contactGroups = server.contactGroups().valueOrNull().orEmpty()
            if (contactGroups.any { it.name.startsWith(TAG_PREFIX) }) {
                server.putContactGroups(contactGroups.filterNot { it.name.startsWith(TAG_PREFIX) })
            }
        }
        peers.forEach { runCatching { it.client.logout(it.token) } }
        runCatching { login.logout() }
        TestMyInfo.clear()
        httpClient.close()
    }

    /** 서버 호출만 하는 상대. */
    class Peer(val id: String, val name: String, val client: TalkServerClient, internal val token: String) {
        private var sent = 0

        suspend fun say(roomId: String, text: String): MessageDto =
            client.sendMessage(roomId, SendMessageRequest("ft-$id-${nowMillis()}-${++sent}", text)).must("상대 보내기")

        suspend fun messages(roomId: String): List<MessageDto> =
            client.messages(roomId, limit = PEER_PAGE).must("상대 대화 조회")

        suspend fun read(roomId: String, messageId: String) {
            client.markRead(roomId, messageId).must("상대 읽음")
        }
    }

    companion object {
        const val ME = "test1"
        const val PEER = "test2"
        const val THIRD = "test3"
        const val AI = "ai"
        const val PASSWORD = "1234"
        /** 이번 실행이 만든 것에 붙는 이름표의 머리말. 정리할 때 이것만 지운다. */
        const val TAG_PREFIX = "FT-"
        private const val PEER_PAGE = 100

        /** 서버 주소가 있으면 준비된 도구를, 없으면 null 을 돌려준다. */
        fun startOrNull(): RealServerHarness? {
            val url = functionalTestServerUrl()
            if (url == null) {
                FT.line("SKIP reason=TALK_FUNCTEST_URL 없음 — 기능 테스트를 돌리지 않는다")
                return null
            }
            TestMyInfo.clear()
            TestLocalSecret.install()
            FT.line("SERVER url=$url")
            return RealServerHarness(url.trimEnd('/'))
        }
    }
}

/** 서버가 받아 준 값. 거절이나 불통이면 무엇이 그랬는지 남기고 단계를 깬다. */
fun <T> ServerResult<T>.must(what: String): T = when (this) {
    is ServerResult.Success -> value
    is ServerResult.Rejected -> throw StepFailure("$what — 서버가 거절했다 status=$status code=$code")
    ServerResult.Unreachable -> throw StepFailure("$what — 서버에 닿지 못했다")
}
