# Talk_Multiplatform

Kotlin Multiplatform(Android + iOS) 메신저 + Ktor 서버. 앱·서버·공유 모듈이 한 저장소에 같이 있다.
사내 메신저 개발 경험을 바탕으로 다시 만든 쇼케이스 프로젝트로, 서버 없이 기기 안 데이터만으로 도는
**로컬 모드**와 서버에 붙는 **서버 모드**를 빌드 설정 하나로 오간다.

---

## 규칙

1. **메모리 이력이나 프로젝트 구조를 모르는 새 PC에서 처음 실행할 때는, 작업을 시작하기 전에
   전체 소스코드 분석을 먼저 진행해줘.** 이 문서와 `README.md` 를 함께 읽어줘.
2. **앱에 구현된 Clean Architecture 와 MVI 계층을 기억해서, 다른 모듈이나 기능을 추가할 때
   기존 코드 플로우와 동일한 로직으로 구현해줘.** 새 패턴을 임의로 도입하지 말고 기존 화면을
   먼저 찾아 그 구조를 따라줘.
3. **한글을 하드코딩하지 말고 `Res.string` 으로 빼서 한글·영문을 모두 지원하게 해줘.**
   기본값은 `composeResources/values/strings.xml`(영문), 한글은 `values-ko/strings.xml` 이다.
   새로 쓰거나 고친 줄에 적용한다. 손대지 않은 기존 하드코딩은 알리기만 한다([작업 방식](#작업-방식) 3).
4. **`//` 로 시작하는 코드 설명 주석은 넣지 마.** 함수나 클래스를 설명하는 `/** */` KDoc 은
   한글로 쓰고 유지해줘. 함수명·변수명은 영어로 써줘.
5. **기능은 Android 와 iOS 양쪽에 모두 적용해줘.** 한쪽만 가능한 기능이면 먼저 가능한지 확인하고
   알려줘. 수정 후에는 양쪽 컴파일을 모두 통과시켜줘.
   ```bash
   ./gradlew :composeApp:compileDebugKotlinAndroid
   ./gradlew :composeApp:compileKotlinIosSimulatorArm64
   ```
6. **서버 응답은 완결된 것만 로컬 DB 와 화면에 반영해줘.** 요청이 실패하거나 응답을 읽지 못하면
   가지고 있던 데이터를 그대로 둔다. 서버가 거절한 것(`ServerResult.Rejected`)과 서버에 닿지 못한
   것(`ServerResult.Unreachable`)을 섞지 마 — 앞쪽은 다시 보내도 같은 답이고, 뒤쪽은 다시 보낼 수 있다.
7. **서버 통신은 `data/repository` 의 `*RepositoryImpl` 에서 처리해줘.** ViewModel 에서 직접
   `TalkServer`(REST)나 `ServerEvents`(WebSocket)를 부르지 마.
8. **앱과 서버의 계약을 바꿀 때는 `:shared` 모듈의 모델을 먼저 고치고 양쪽을 같이 맞춰줘.**
   한쪽만 바꾸면 런타임에서만 깨진다. 서버를 건드렸으면 서버 테스트도 같이 돌려줘.
   ```bash
   ./gradlew :server:test
   ```
9. **코드를 수정한 뒤에는 테스트를 돌리고, Android 에뮬레이터와 iOS 시뮬레이터를 띄워서 양쪽
   플랫폼에서 실제로 동작하는지 확인해줘.** 컴파일만 통과시키고 끝내지 마.
   ```bash
   ./gradlew :composeApp:testDebugUnitTest
   ```
10. **로그 검증과 시뮬레이터 테스트 검증이 모두 끝나면 자동으로 커밋하고 푸시해줘.**
    검증이 끝나기 전에는 커밋하지 마.
11. **수정·추가·개선하는 기능마다 브랜치를 새로 따서 진행하고, 10번 검증이 끝나면 머지해줘.**
    브랜치 이름은 작업 내용을 알 수 있게 지어줘. 기본 브랜치는 `main` 이다.

## 작업 방식

흔한 코딩 실수를 줄이기 위한 일반 지침이다. **위 규칙과 부딪히면 위 규칙이 우선한다.**
속도보다 신중함 쪽으로 기울어 있으니, 사소한 작업은 판단해서 가볍게 한다.

### 1. 코딩 전에 생각하기

- 가정은 드러내서 말한다. 해석이 여러 개면 조용히 하나를 고르지 말고 제시한다.
- 더 단순한 방법이 있으면 말한다. 요청이 문제 있어 보이면 반대 의견을 낸다.
- **묻는 기준** — 요구사항 해석이 갈리거나, 되돌리기 어려운 결정(서버·사람에게 나가는 동작, 데이터
  삭제, DB 스키마 변경)이면 멈추고 묻는다. 구현 세부는 묻지 않고 규칙 2대로 기존 코드를 따라
  진행한 뒤, 무엇을 가정했는지 알린다.

### 2. 단순하게

- 요청한 것 이상의 기능, 한 번만 쓰는 코드의 추상화, 요청하지 않은 설정·확장성을 넣지 않는다.
- 일어날 수 없는 경우의 예외 처리는 넣지 않는다. 단, 서버 응답 누락·연결 끊김·타임아웃은
  **일어나는 일**이다(규칙 6).
- 200줄로 쓴 것이 50줄로 될 수 있으면 다시 쓴다. "시니어가 보면 과하다고 할까?" 를 스스로 묻는다.

### 3. 필요한 곳만 고치기

- 요청과 상관없는 옆 코드·주석·포맷을 "개선" 하지 않는다. 고장 나지 않은 것은 리팩토링하지 않는다
  (리팩토링 자체를 요청받은 경우는 제외).
- 관련 없는 죽은 코드를 보면 알리기만 하고 지우지 않는다.
- 내 변경 때문에 쓰이지 않게 된 import·변수·함수는 지운다.
- 기준: 바뀐 줄마다 사용자의 요청으로 거슬러 올라갈 수 있어야 한다.

### 4. 목표를 검증할 수 있게

- 작업을 확인 가능한 목표로 바꾼다.
  - 검증 추가 → 잘못된 입력 테스트를 먼저 쓰고 통과시킨다
  - 버그 수정 → 재현 테스트를 먼저 쓰고 통과시킨다
  - 리팩토링 → 전후로 테스트가 통과하는지 본다
- 여러 단계 작업은 짧은 계획을 먼저 적는다: `1. [단계] → 확인: [무엇으로]`
- 이 프로젝트에서 "끝" 은 규칙 5·9·10 이다 — 양쪽 컴파일, 에뮬레이터·시뮬레이터 동작 확인, 커밋·머지.

잘 되고 있다면 diff 에 불필요한 변경이 줄고, 과하게 짜서 다시 쓰는 일이 줄고, 질문이 실수 뒤가
아니라 구현 전에 나온다.

## 구조

```
composeApp/src/
  commonMain/kotlin/com/eunilsung/talk/
    data/      local(플랫폼 추상화 · repository(로컬 모드 구현) · seed(시연용 데이터)) · mapper
               remote/ server(REST·WebSocket·파일·사용자 목록) · push · linkpreview · share
               repository(서버 모드 구현)
    domain/    model · repository(interface) · usecase
    ui/        도메인별 화면 + uikit(공용 컴포넌트) + theme
    di/        Modules.kt (Koin appModule)
    Config.kt  기능 온오프 · 서버 모드 여부 · 내 정보
  commonMain/sqldelight/         AppDatabase.sq (기기 안 캐시)
  commonMain/composeResources/   values(영문) · values-ko(한글) · drawable
  androidMain/ Android actual + platformModule
  iosMain/     iOS actual + platformModule
shared/        앱·서버 공용 요청/응답 모델 (com.eunilsung.talk.shared.api)
server/        Ktor 서버 (Netty · WebSocket · HikariCP · Flyway · PostgreSQL)
iosApp/        Xcode 프로젝트
.maestro/      실화면 흐름 (Android·iOS 공용 Maestro 흐름 · 상대 역할 스크립트)
```

- Clean Architecture(data/domain/ui) + MVI 변형(Action → ViewModel → `StateFlow<UiState>`)
- DI = Koin, 네비게이션 = Voyager, 이미지 = coil3, 통신 = ktor, 기기 DB = SQLDelight
- 의존성은 안쪽으로만 흐른다. `domain` 은 `ui`·`data` 를 모르고 Compose 타입도 쓰지 않는다
- 작업별 세부는 그 폴더의 `CLAUDE.md` 에 있다 — `deploy/`(배포·테스트 서버), `.maestro/`(실화면 흐름),
  `composeApp/src/commonTest/.../functest/`(기능 테스트). 그 폴더의 파일을 다룰 때 함께 읽힌다.

### 계층 규칙

새 화면·기능을 만들 때 이 모양을 따른다. 앞의 세 가지는 지금 모든 화면이 지킨다.

- **화면은 ViewModel 만 본다.** Composable 이 저장소·유스케이스를 `koinInject` 로 가져와 부르지 않는다.
  예외는 한 번 부르고 끝나는 기기 기능뿐이다 — `data/local` 의 `ExternalUrlOpener`·`AppExiter`·
  `AppVersionProvider`·`VideoThumbnailLoader`, 그리고 `Platform`. 파일을 읽고 저장하고 결과를 알리는 흐름
  (받기·열기·사진첩 저장)은 ViewModel → 유스케이스 → `DeviceFileRepository` 로 간다.
  앱 전체가 쓰는 값(프로필 사진 주소)은 `MainViewModel` 의 상태를 CompositionLocal 로 내려 준다
  (`LocalProfileImageUrls`). 목록 항목마다 ViewModel 을 두지 않기 위해서다.
- **ViewModel 은 유스케이스만 쓴다.** 저장소·`TalkServer`·`ServerEvents` 를 받지 않는다. 기기 기능 추상화
  (사진첩·파일 고르기)는 받아도 된다. 유스케이스는 `domain/usecase` 에 두고 `di/Modules.kt` 에 등록한다.
  여러 개를 쓰는 화면은 `XUseCases` 묶음으로 받는다. 여러 저장소를 잇는 업무 흐름(1:1 방 열기
  `StartDirectChatUseCase`, 나간 상대 재초대, 공유 보내기)도 유스케이스가 한다. ViewModel 은 결과를 상태와
  이벤트로 바꾸기만 한다.
- **내 아이디·이름은 `GetMyUserUseCase` 로 얻는다.** ViewModel·화면은 `Config.MyInfo` 를 읽지 않는다.
  화면이 알아야 하면 ViewModel 이 상태에 실어 준다(대화방 `myId`, 프로필 `isMe`, 워터마크 `MainUiState.me`).
- **화면은 `onAction` 으로만 ViewModel 에 일을 시킨다.** ViewModel 마다 `XActions`(sealed)·`XUiState`·
  `onAction` 을 둔다. 다이얼로그·이동·토스트처럼 한 번만 처리할 일은 `XEvent` 를 `Channel` 로 내보낸다
  (`events`). 액션은 `XActions.kt`, 상태는 `XUiState.kt` 에 둔다. 화면의 `LaunchedEffect`·`DisposableEffect`
  에서 할 일도 액션으로 보낸다(대화방의 `OnEnterRoom`·`OnLeaveRoom`).
- **상태는 `uiState` 하나로 모은다.** ViewModel 은 `StateFlow<XUiState>` 하나와 `events` 만 내놓는다
  (대화방 `ChatRoomUiState` 참고). 검색어·선택·배지처럼 화면이 보는 값은 모두 `XUiState` 의 칸이다.
  대화방의 스크롤 신호(`ChatRoomSignals`)만 예외다 — 여러 곳이 같은 신호를 받고, 진입 스크롤은 마지막 값을
  다시 받아야 한다.
- **도메인 모델은 화면을 모른다.** 선택 표시·묶음 표시처럼 화면에만 필요한 값은 ui 쪽 상태에 둔다
  (`GroupedChat`, 고른 id 집합). 화면 모양으로 바꾼 모델과 그 변환도 ui 쪽에 둔다(투표의 `VoteDetail`·
  `VoteResult` 는 `VoteUiState.kt`). 내가 보낸 대화인지(`isMe`)는 로그인한 계정을 아는 매퍼·저장소가 채운다.
- **코드 값은 이름 붙은 판정으로 읽는다.** 알림방 `$`, 고정 그룹 `-`, 알림 끔 `"1"`, 대화그룹 종류 `"1"/"2"`,
  기본그룹 `"0"` 을 직접 비교하지 않고 `domain/model` 의 판정을 쓴다(`isNotificationRoom`·`isSystemGroup`·
  `isMuted`·`hasUnread`·`isUnreadGroup`·`isDefaultGroup`). 방 찾기는 보이는 이름으로 한다(`matchesTitle`),
  사람 찾기는 이름·초성·전화번호로 한다(`filterMembers`).
- **바깥 모양을 도메인 모델로 바꾸는 일은 매퍼가 한다**(`data/mapper`). 서버 응답·기기 DB 행·저장용 JSON·
  시연용 데이터(`SeedMapper`)가 모두 해당한다. 저장소는 주고받기·저장과 반영만 하고, 새 대화·새 공지처럼
  업무 중에 새로 생기는 도메인 객체만 직접 만든다.
- **`domain/repository` 에는 인터페이스만 둔다.** 주고받는 상태·결과 타입은 `domain/model` 에 둔다
  (`ChatSettingsState`·`AddUserResult`). `util` 은 계층을 모르는 순수 도우미다. domain 이 쓰는 도우미는
  `domain/util`, Compose 단위(`Dp`)를 쓰는 것은 ui 에 둔다. 대화 미리보기 문구(`util/ChatPreview.kt`)만
  data·ui 가 함께 써서 util 에 남는다.
- **화면 함수 이름** — `XContent` 는 화면 전체를 그리는 상태 없는 함수, `XBody` 는 공용 스캐폴드
  (`SettingDetailScaffold`·`OverlayScaffold`) 안에 들어가는 본문이다.
- **ViewModel 이 커지면 역할별 처리기로 나눈다.** 대화방은 사진·파일(`ChatMediaHandler`), 찾기
  (`ChatSearchHandler`), 읽음(`ChatReadHandler`)을 따로 둔다. 처리기는 ViewModel 의 scope 와 상태를 받아 쓰고
  화면에는 드러나지 않는다.

### 로컬 모드와 서버 모드

`local.properties` 의 `server.baseUrl` 이 비어 있으면 로컬 모드, 값이 있으면 서버 모드로 빌드된다
(`Config.Server.IS_ENABLED`). 어느 저장소 구현을 쓸지는 `di/Modules.kt` 한 곳에서 갈린다.

- **로컬 모드** — `data/local/repository/Local*RepositoryImpl` 이 시연용 데이터를 기기 DB 에 채워 쓴다.
- **서버 모드** — `data/repository/*RepositoryImpl` 이 서버와 주고받는다. 화면은 계속 기기 DB 를
  보고, 서버 구현은 그 위에 얹혀(`by local` 위임) 서버에서 받은 것을 기기 DB 에 반영한다.
  서버 모드는 기기 DB 파일을 따로 쓴다(`Config.Database.NAME`).

서버 모드 저장소를 고칠 때 지켜야 할 것:

- **화면에 먼저 그려 두지 않는다.** 공감·회수·공지·그룹 변경은 서버가 받아 준 결과만 반영한다.
  대화 전송만 예외로, '전송 중'으로 먼저 보이고 결과에 따라 완료·실패로 바뀐다.
- **대화 id 는 앱이 만들고 재전송에도 바꾸지 않는다.** 서버가 같은 id 를 한 번만 받는다.
- **WebSocket 알림만으로 로컬을 채우지 않는다.** 이번 실행에서 서버와 맞춰 본 방에만 알림 대화를
  저장한다. 맞춰 보지 않은 방에 끼워 넣으면 그 앞 구간이 영영 빈다.
- **연결될 때마다 다시 조회한다.** 끊겨 있던 동안의 알림은 다시 오지 않는다.

### 서버

- 스키마의 정본은 `server/src/main/resources/db/migration` 의 Flyway SQL 이다.
  **이미 배포된 마이그레이션 파일은 고치지 않는다.** 바꿀 것이 있으면 새 번호로 추가한다.
- 신원은 토큰이 정한다. 본문이나 쿼리에 적힌 아이디는 믿지 않는다(`callerUserId`).
- 참여 중이 아닌 방은 없는 방과 똑같이 답한다. 저장소 함수가 요청한 사람을 함께 받는다.
  들어오기 전(`joined_seq` 이하)의 대화·첨부·투표도 없는 것과 똑같이 답한다 — 읽음·안읽음, 앞뒤 조회도 마찬가지다.
  같은 id 로 다시 보내면 내가 보낸, 볼 수 있는 대화일 때만 그 대화를 돌려준다. 남의 대화나 들어오기 전 대화의 id 면 409 `message_id_taken` 이고,
  부가 정보가 틀린 요청은 id 가 있든 없든 그보다 먼저 400 이다.
- 대화의 부가 정보(`MessagePayloadDto`)는 앱이 보낸 그대로 저장하지 않는다. 종류마다 허용한 칸만 남겨 다시 만든다 —
  답장 인용은 `replyToId` 의 원본에서, 파일 이름·크기는 올린 기록에서 채우고, 서버만 만드는 칸(투표·공지·초대 대상)은 버린다.
- 대화에 실린 파일은 부가 정보의 `fileId` 가 아니라 서버가 확인해 적은 `chat_message.file_id` 로 따진다(회수 때 지우기, 받기, 다시 싣기 금지).
- 한 사람이 서버 자원을 혼자 쓰지 못하게 한도를 둔다(`limits/ServerLimits`) — 본문 크기, 하루 파일, 동시 올리기 수·시간, 방 만들기·초대·투표 만들기 횟수,
  방 인원, 알림 연결 수, 푸시 토큰 수. 확인과 기록이 따로 돌면 동시 요청이 함께 통과하므로, 하루 파일 한도는 사람별 잠금 안에서,
  방 인원은 방 행 잠금 안에서 센다. 본문을 읽는 동안 스레드를 붙잡지 않는다(느린 올리기 몇십 개로 IO 스레드가 바닥난다).
- 부가 정보(JSON)는 직렬화한 길이에도 DB 상한(`CHECK`, 32768자)이 있다. 서버가 새 칸을 싣게 되면 그 칸에도 길이 상한을 둔다.
- 안읽음 수·득표수처럼 계산할 수 있는 값은 저장하지 않는다.
- 바꾸는 요청은 전부 REST 로 받고, WebSocket 은 "무엇이 바뀌었다"를 알리는 데만 쓴다.
- 서버 테스트는 내장 PostgreSQL 로 실제 쿼리를 돌린다. 설치할 것이 없다.

### AI Agent 연동 (AI 요약)

문서 파일 대화의 롱프레스 메뉴 "AI 요약" 이 같은 기기의 AI Agent 앱(별도 저장소)을 링크로 연다. 요약은 그 앱이 하고,
Talk 서버·`:shared` 계약은 바뀌지 않는다(AI Agent 쪽이 사용자 토큰으로 `GET /files/{id}` 를 부른다).

- **링크 계약** — `aiagent://summarize?fileId=<서버 파일 id>&fileName=<파일 이름>&userId=<로그인 아이디>&userName=<이름>`.
  값은 모두 퍼센트 인코딩(공백 `%20`). 받는 쪽이 값을 검증하고 계정을 비교하므로, 이름·순서를 바꾸려면 AI Agent 와 함께 바꾼다.
  만드는 곳은 `data/mapper/AiAgentLinkMapper`.
- **흐름** — `ChatActionMenu`(`canRequestAiSummary`) → `ChatRoomActions.OnRequestAiSummary` → `RequestAiSummaryUseCase` →
  `AiSummaryRepository`(파일 id 찾기·링크 열기) → 결과에 따라 `ChatRoomEvent.AiSummaryFileNotReady`·`AiAgentNotInstalled` 토스트.
  두 토스트에는 `TestTags.TOAST_AI_SUMMARY_FILE_NOT_READY`·`TOAST_AI_AGENT_NOT_INSTALLED` 가 붙는다(`ToastManager.show` 의 `testTag`).
- **파일 id** — 말풍선의 `imagePath`·`localPath` 는 기기 캐시 경로라 서버 id 가 아니다. 같은 기기 파일을 여러 방에 보내거나 전달하면
  경로 하나를 id 여럿이 가리키므로, `ChatRoomRepositoryImpl` 이 서버가 확정한 파일 대화를 반영할 때마다(받은 대화 저장·이전 대화·
  알림 대화·올리고 보내기·전달) `ServerFileStore.rememberChatFile` 로 `server_chat_file_<방 id>/<대화 id>` → 파일 id 를 적는다.
  유스케이스는 방·대화로 먼저 찾고(`chatFileId`), 그 기록이 없는 옛 대화만 `fileIdOf(path)` 로 `server_file_<id>` 를 거꾸로 훑는다.
  둘 다 없으면 "받는 중" 안내가 뜬다.
- **열기** — `data/local/AppLinkOpener`(Android `ACTION_VIEW`, iOS `canOpenURL`→`openURL`). iOS 는 Info.plist 의
  `LSApplicationQueriesSchemes` 에 `aiagent` 가 있어야 한다. 메뉴는 서버 모드·`Config.ChatRoom.IS_AI_SUMMARY_ENABLED`·
  `AI_SUMMARY_EXTENSIONS`·회수 안 됨·전송 완료일 때만 보인다.

## 개발 환경 준비

새 머신에서 클론했다면 이것들이 저장소에 없으므로 따로 준비해야 한다.

1. **JDK 17** — Gradle 과 서버가 17 을 쓴다. PATH 의 java 가 더 낮으면 `JAVA_HOME` 을 17 로 잡는다.
2. **`local.properties`** — gitignore 대상이다.
   ```properties
   sdk.dir=<Android SDK 경로>
   # 서버 모드로 빌드할 때만. 비우면 로컬 모드다.
   # Android 에뮬레이터: http://10.0.2.2:8090 / iOS 시뮬레이터: http://localhost:8090
   server.baseUrl=http://10.0.2.2:8090
   # 전체 검증(verifyAll)이 쓸 이 PC 의 기기. 다른 작업과 겹치지 않는 것을 고른다. 꺼져 있으면 켠다
   e2e.androidAvd=<에뮬레이터 이름>
   e2e.iosDevice=<시뮬레이터 UDID 또는 이름>
   ```
3. **Firebase 서비스 계정 키** — 서버가 푸시를 보낼 때만 필요하다. 저장소에 없으니 Firebase 콘솔
   (`talk-multiplatform` 프로젝트)에서 받아 `FIREBASE_CREDENTIALS=<파일 경로>` 로 넘긴다.
   없으면 푸시만 꺼지고 나머지는 그대로 동작한다.
4. **Gemini API 키** — AI 참여자·말풍선 번역·글 다듬기에만 필요하다. Google AI Studio 에서 받아 서버를 띄우는
   환경에 `GEMINI_API_KEY` 로 넘긴다(모델은 `AI_MODEL`, 기본 `gemini-3.5-flash-lite`).
   없으면 AI 만 꺼지고 나머지는 그대로 동작한다.
5. **Maestro 와 Xcode** — 실화면 흐름에만 필요하다. `curl -fsSL https://get.maestro.mobile.dev | bash` 로 설치한다.
   iOS 앱 빌드에는 Xcode 가 있어야 하고, 명령줄 도구가 Xcode 를 가리켜야 한다(`sudo xcode-select -s /Applications/Xcode.app`).
   새 Mac 의 준비 순서는 README 의 "검증 환경 준비" 에 있다.

## 실행

```bash
./gradlew :server:runLocal             # 로컬 서버 (포트 8090) + 내장 PostgreSQL (포트 54329)
./gradlew :composeApp:installDebug     # Android 설치
open iosApp/iosApp.xcodeproj           # iOS 는 Xcode 에서 시뮬레이터 선택 후 실행
```

- 로컬 서버의 데이터는 `server/.localdb`, 올린 파일은 `server/.localfiles` 에 남는다. 지우면 처음부터다.
- 서버 주소는 빌드할 때 상수로 박힌다. `server.baseUrl` 을 바꿨으면 앱을 다시 빌드해야 한다.
- 운영 진입점(`:server:run`)은 `DB_URL`·`DB_USER`·`DB_PASSWORD`·`FILES_DIR`·`PORT`·`HOST` 환경변수를 읽는다.
  `SERVER_ONLY=1` 을 주면 앱 모듈을 빼고 서버만 구성한다(배포용).
- **로컬 서버는 이 PC 안(127.0.0.1)에서만 받는다.** 에뮬레이터(`10.0.2.2`)·시뮬레이터(`localhost`)는 그대로 붙고, 실기기는
  `adb reverse tcp:8090 tcp:8090` 뒤 `localhost:8090` 으로 붙인다. 같은 Wi-Fi 로 꼭 붙여야 할 때만 `HOST=0.0.0.0 ./gradlew :server:runLocal`.
  그때는 공개된 시연용 비밀번호로 같은 망의 누구나 들어올 수 있어, 넉넉한 로컬 한도 대신 운영 한도를 쓰고 뜰 때 경고를 찍는다.
- 로컬 서버(`:server:runLocal`)는 시연용 계정 `test1`~`test10` / `1234` 를 만들어 둔다. 운영 진입점에는 기본 비밀번호가 없다 —
  `SEED_DEMO_ACCOUNTS=true` 와 `SEED_PASSWORD` 가 함께 있을 때만 그 비밀번호로 만든다(`deploy/CLAUDE.md`).

## 배포

운영 구성은 `deploy/` 에 있다 — PostgreSQL + 서버 + Caddy(HTTPS)를 Docker Compose 로 띄운다.

```bash
DEPLOY_HOST=<SSH 사용자>@<테스트 서버 공인 IP> DEPLOY_KEY=~/.ssh/<키 파일> deploy/deploy.sh   # 빌드 → 올리기 → 다시 띄우기 → /health 확인
```

- **테스트 서버**는 `https://<테스트 서버 주소>` 다. 같은 클라우드 계정의 다른 인스턴스는 다른 프로젝트의 것이니 건드리지 않는다.
- **실제 주소·SSH 사용자·키 파일 이름은 저장소에 적지 않는다**(공개 저장소). 실제 값은 저장소 밖 `../Talk_info/` 에 있다.
  예전 커밋에는 남아 있으니 주소는 알려진 것으로 보고, 계정 비밀번호(`SEED_PASSWORD`)와 한도로 막는다.
- 테스트 서버 사양·비밀값·인스턴스 준비·배포한 서버 확인 방법은 `deploy/CLAUDE.md` 에 있다.

## 검증 방법

규칙 9·10 의 검증을 실제로 수행하는 방법이다.

- **동작 확인은 에뮬레이터·시뮬레이터에서 한다.** 사용자 실기기는 사용자가 붙여 주고 요청했을 때만
  쓴다. 앱 밖으로 인텐트를 쏘는 등 사용자 화면을 점유하는 조작은 하지 않는다.
- **두 번째 사용자는 `curl` 로 대신할 수 있다.** 다른 계정으로 로그인해 토큰을 받고 REST 를 부르면,
  에뮬레이터 한 대로도 주고받기·읽음·알림을 확인할 수 있다.
- **로그 검증**은 서버 로그(요청마다 한 줄씩 남는다)와 `adb logcat` 을 함께 본다.
  `adb logcat` · `dumpsys` 같은 읽기 전용 조사는 언제든 해도 된다.
- **탭이 먹는지 검증할 때 `adb shell input tap` 을 믿지 말 것.** MOVE 이벤트가 없어서 실제 손가락과
  다르게 동작한다. 부모 레이어가 포인터 이벤트를 소비하면 자식 `clickable` 이 죽는데 `input tap`
  으로는 재현되지 않는다. `input motionevent DOWN/MOVE/UP` 을 쓴다.
- **핀치줌·더블탭 같은 멀티터치 제스처는 adb 로 주입하기 어렵다.** 이 경우만 구현·설치까지 하고
  사용자에게 확인을 요청한다.
- 스크린샷 픽셀을 직접 읽어 판정하면 UI 상태를 확실하게 검증할 수 있다.
- WebSocket 을 쓰는 서버 테스트는 연결이 서버에 등록된 것을 확인한 뒤 다음 단계로 간다.
  연 직후에 일어난 일은 알림으로 오지 않는다.

### 기능 테스트 — 진짜 서버에 붙여 돌린다

앱 테스트는 서버 대역을, 서버 테스트는 클라이언트 대역을 쓴다. 그래서 한쪽만 계약을 바꿔도 양쪽
테스트가 모두 통과할 수 있다. 기능 테스트(`composeApp/src/commonTest/.../functest`)는 앱이 실제로 쓰는
통신 코드(`TalkServerClient`·`TalkSocket`)와 저장소로 진짜 서버에 붙어 그 어긋남을 잡는다.

```bash
./gradlew :composeApp:testDebugUnitTest -PfunctionalTest=true --tests "*functest*"
```

- **`-PfunctionalTest=true` 가 없으면 돌지 않는다.** 평소 `testDebugUnitTest` 에서는 서버로 아무것도 나가지
  않고 통과한다. 서버를 건드렸거나 `:shared` 의 계약을 바꿨으면 켜고 돌린다.
- 새 기능을 서버에 붙였으면 해당 묶음에 시나리오를 더한다.
- 실행 구성·결과 읽는 법·묶음 목록·시나리오 작성 규칙은 `functest/CLAUDE.md` 에 있다.

### 실화면 흐름 — Maestro

기능 테스트가 비워 둔 "버튼과 액션의 연결"을 에뮬레이터·시뮬레이터에서 실제로 눌러 본다. 같은 흐름 파일을
Android 와 iOS 에 그대로 돌린다(`.maestro`).

```bash
TALK_SERVER_BASE_URL=http://localhost:8090 ./gradlew :composeApp:e2eAndroid   # 또는 :composeApp:e2eIos
```

- **요소는 `TestTags`(`ui/uikit/TestTags.kt`)의 이름을 `id` 로 찾는다.** 화면 문구는 기기 언어마다 다르고 같은 문구가
  여러 곳에 나온다(로그인의 "비밀번호" 라벨과 입력칸 안내). 흐름이 누르는 요소를 새로 만들면 태그를 붙이고
  흐름 파일에도 같은 이름을 쓴다. 상대가 보낸 말·방 이름처럼 흐름이 만든 영문 글자는 글자로 찾아도 된다.
- **다이얼로그는 Surface 에 `exposeTestTags()` 를 붙인다.** Android 는 다이얼로그가 창이 따로라 앱 루트의 설정이
  닿지 않는다. iOS 는 testTag 가 그대로 접근성 식별자로 나간다.
- **흐름이 확인하는 토스트는 `ToastManager.show(…, testTag = TestTags.X)` 로 띄운다.** 토스트 문구도 앱 문자열이라 글자로 찾지 않는다.
  안내가 여럿이면 태그를 따로 두어 구분한다(AI 요약의 미설치·받는 중).
- 실행 스크립트·상대 역할·기기별 주의점은 `.maestro/CLAUDE.md` 에 있다.

### 전체 검증 — 실행 구성 하나로

Android Studio 에서 실행 구성 `검증 - 전체 (실화면 포함)` 을 고르고 실행하면 규칙 9 의 검증이 차례로 돈다.
고칠 때마다는 `검증 - 빠르게 (JVM)`(서버 테스트와 앱·기능 테스트)을, 머지 전에는 전체를 돌린다. 전체는 8분쯤
걸린다(처음 iOS 앱을 빌드할 때는 패키지를 받느라 더 걸린다).

```bash
TALK_SERVER_BASE_URL=http://localhost:8090 ./gradlew :composeApp:verifyAll -PfunctionalTest=true --continue
```

1. 서버 테스트 → 로컬 서버 띄우기 → 앱 테스트와 기능 테스트(JVM) → 기능 테스트(iOS 시뮬레이터)
2. Android 앱 빌드 → 에뮬레이터에 설치 → 실화면 흐름 → iOS 앱 빌드 → 시뮬레이터에 설치 → 실화면 흐름
3. 띄운 서버는 끝나면 내려간다. 하나가 깨져도 끝까지 돌고(`--continue`), 깨진 단계가 마지막에 모여 나온다.

- **기기는 `local.properties` 의 `e2e.androidAvd`·`e2e.iosDevice` 로 고른다.** iOS 기능 테스트도 같은 시뮬레이터에서 돈다.
  없으면 켜져 있는 에뮬레이터·iPhone 시뮬레이터 중 첫 번째를 쓰고, 실기기는 고르지 않는다.
- **설정이 맞지 않으면 시작하자마자 멈춘다.** 기능 테스트가 꺼져 있거나(`-PfunctionalTest=true` 없음) 앱이 로컬 서버가
  아닌 주소로 빌드되면, 오래 돈 뒤가 아니라 처음에 이유를 알려 준다.
- **자동으로 확인하지 못하는 것** — 푸시의 실제 도착, 핀치줌 같은 멀티터치, 카메라, iOS 한글 입력. AI 단계는 키가 없는
  서버에서 건너뛴다. 이것들은 해당 기능을 고쳤을 때 손으로 확인한다.

## 주의

- **로컬 서버 포트는 8090 이다.** 운영 기본값 8080 을 피한 것으로, 같은 PC 에서 다른 프로젝트의
  서버와 겹치지 않게 하려는 것이다.
- **파일 하나는 20MB 까지다**(`MAX_FILE_BYTES`). 받는 쪽 앱은 파일을 기기 캐시에 내려받아 쓴다.
- **푸시는 Android 실기기에서만 도착을 확인했다**(테스트 서버, 2026-10-20). Firebase 프로젝트는 `talk-multiplatform`
  이고 테스트 서버 `.env` 에 서비스 계정 키가 들어 있다. iOS 는 APNs 키가 Firebase 에 등록되지 않아 아직 오지 않는다.
