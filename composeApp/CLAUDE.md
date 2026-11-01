# 앱 — Talk_Multiplatform

루트 `CLAUDE.md` 의 규칙이 먼저다. 경로는 저장소 루트 기준이다.

## 규칙

- **새 패턴을 임의로 도입하지 않는다.** 다른 모듈이나 기능을 추가할 때는 기존 화면을 먼저 찾아 같은 구조로 만든다.
- **한글을 하드코딩하지 않는다.** `Res.string` 으로 빼서 한글·영문을 모두 지원한다. 기본값은 `composeResources/values/strings.xml`(영문),
  한글은 `values-ko/strings.xml` 이다. 새로 쓰거나 고친 줄에 적용하고, 손대지 않은 기존 하드코딩은 알리기만 한다.
- **기능은 Android 와 iOS 양쪽에 적용한다.** 한쪽만 가능한 기능이면 먼저 가능한지 확인하고 알린다. 수정 후에는 양쪽 컴파일을 통과시킨다.
  ```bash
  ./gradlew :composeApp:compileDebugKotlinAndroid
  ./gradlew :composeApp:compileKotlinIosSimulatorArm64
  ```
- **서버 응답은 완결된 것만 로컬 DB 와 화면에 반영한다.** 요청이 실패하거나 응답을 읽지 못하면 가지고 있던 데이터를 그대로 둔다.
  서버가 거절한 것(`ServerResult.Rejected`)은 다시 보내도 같은 답이고, 서버에 닿지 못한 것(`ServerResult.Unreachable`)은 다시 보낼 수 있다. 둘을 섞지 않는다.
- **서버 통신은 `data/repository` 의 `*RepositoryImpl` 에서 처리한다.** ViewModel 에서 `TalkServer`(REST)나 `ServerEvents`(WebSocket)를 부르지 않는다.
- 서버 응답 누락·연결 끊김·타임아웃은 일어나는 일이다. 예외 처리를 생략하지 않는다.

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
```

- Clean Architecture(data/domain/ui) + MVI 변형(Action → ViewModel → `StateFlow<UiState>`)
- DI = Koin, 네비게이션 = Voyager, 이미지 = coil3, 통신 = ktor, 기기 DB = SQLDelight
- 의존성은 안쪽으로만 흐른다. `domain` 은 `ui`·`data` 를 모르고 Compose 타입도 쓰지 않는다

## 계층 규칙

- **화면은 ViewModel 만 본다.** Composable 이 저장소·유스케이스를 `koinInject` 로 가져와 부르지 않는다.
  예외는 한 번 부르고 끝나는 기기 기능뿐이다 — `data/local` 의 `ExternalUrlOpener`·`AppExiter`·`AppVersionProvider`·`VideoThumbnailLoader`, 그리고 `Platform`.
  파일을 읽고 저장하고 결과를 알리는 흐름(받기·열기·사진첩 저장)은 ViewModel → 유스케이스 → `DeviceFileRepository` 로 간다.
  앱 전체가 쓰는 값(프로필 사진 주소)은 `MainViewModel` 의 상태를 CompositionLocal 로 내려 준다(`LocalProfileImageUrls`). 목록 항목마다 ViewModel 을 두지 않기 위해서다.
- **ViewModel 은 유스케이스만 쓴다.** 저장소·`TalkServer`·`ServerEvents` 를 받지 않는다. 기기 기능 추상화(사진첩·파일 고르기)는 받아도 된다.
  유스케이스는 `domain/usecase` 에 두고 `di/Modules.kt` 에 등록한다. 여러 개를 쓰는 화면은 `XUseCases` 묶음으로 받는다.
  여러 저장소를 잇는 업무 흐름(`StartDirectChatUseCase`, 나간 상대 재초대, 공유 보내기)도 유스케이스가 한다. ViewModel 은 결과를 상태와 이벤트로 바꾸기만 한다.
- **내 아이디·이름은 `GetMyUserUseCase` 로 얻는다.** ViewModel·화면은 `Config.MyInfo` 를 읽지 않는다. 화면이 알아야 하면 ViewModel 이 상태에 실어 준다.
- **화면은 `onAction` 으로만 ViewModel 에 일을 시킨다.** ViewModel 마다 `XActions`(sealed)·`XUiState`·`onAction` 을 둔다.
  다이얼로그·이동·토스트처럼 한 번만 처리할 일은 `XEvent` 를 `Channel` 로 내보낸다(`events`). 액션은 `XActions.kt`, 상태는 `XUiState.kt` 에 둔다.
  화면의 `LaunchedEffect`·`DisposableEffect` 에서 할 일도 액션으로 보낸다(대화방의 `OnEnterRoom`·`OnLeaveRoom`).
- **상태는 `uiState` 하나로 모은다.** ViewModel 은 `StateFlow<XUiState>` 하나와 `events` 만 내놓는다. 화면이 보는 값은 모두 `XUiState` 의 칸이다.
  대화방의 스크롤 신호(`ChatRoomSignals`)만 예외다 — 여러 곳이 같은 신호를 받고, 진입 스크롤은 마지막 값을 다시 받아야 한다.
- **도메인 모델은 화면을 모른다.** 선택 표시·묶음 표시처럼 화면에만 필요한 값과 화면 모양으로 바꾼 모델은 ui 쪽에 둔다(`GroupedChat`, `VoteUiState.kt`).
  내가 보낸 대화인지(`isMe`)는 로그인한 계정을 아는 매퍼·저장소가 채운다.
- **코드 값은 이름 붙은 판정으로 읽는다.** 알림방 `$`, 고정 그룹 `-`, 알림 끔 `"1"`, 대화그룹 종류 `"1"/"2"`, 기본그룹 `"0"` 을 직접 비교하지 않고
  `domain/model` 의 판정을 쓴다(`isNotificationRoom`·`isSystemGroup`·`isMuted`·`hasUnread`·`isUnreadGroup`·`isDefaultGroup`).
  방 찾기는 보이는 이름으로(`matchesTitle`), 사람 찾기는 이름·초성·전화번호로 한다(`filterMembers`).
- **바깥 모양을 도메인 모델로 바꾸는 일은 매퍼가 한다**(`data/mapper`). 서버 응답·기기 DB 행·저장용 JSON·시연용 데이터(`SeedMapper`)가 해당한다.
  저장소는 주고받기·저장과 반영만 하고, 새 대화·새 공지처럼 업무 중에 새로 생기는 도메인 객체만 직접 만든다.
- **`domain/repository` 에는 인터페이스만 둔다.** 주고받는 상태·결과 타입은 `domain/model` 에 둔다.
  `util` 은 계층을 모르는 순수 도우미다. domain 이 쓰는 도우미는 `domain/util`, Compose 단위(`Dp`)를 쓰는 것은 ui 에 둔다.
  대화 미리보기 문구(`util/ChatPreview.kt`)만 data·ui 가 함께 써서 util 에 남는다.
- **화면 함수 이름** — `XContent` 는 화면 전체를 그리는 상태 없는 함수, `XBody` 는 공용 스캐폴드(`SettingDetailScaffold`·`OverlayScaffold`) 안의 본문이다.
- **ViewModel 이 커지면 역할별 처리기로 나눈다.** 대화방의 `ChatMediaHandler`·`ChatSearchHandler`·`ChatReadHandler` 처럼 ViewModel 의 scope 와 상태를 받아 쓰고 화면에는 드러내지 않는다.

## 로컬 모드와 서버 모드

`local.properties` 의 `server.baseUrl` 이 비어 있으면 로컬 모드, 값이 있으면 서버 모드로 빌드된다(`Config.Server.IS_ENABLED`).
어느 저장소 구현을 쓸지는 `di/Modules.kt` 한 곳에서 갈린다. 주소는 빌드할 때 상수로 박히므로 바꾸면 앱을 다시 빌드한다.
Android 에뮬레이터는 `http://10.0.2.2:8090`, iOS 시뮬레이터는 `http://localhost:8090` 이다.

- **로컬 모드** — `data/local/repository/Local*RepositoryImpl` 이 시연용 데이터를 기기 DB 에 채워 쓴다.
- **서버 모드** — `data/repository/*RepositoryImpl` 이 서버와 주고받는다. 화면은 계속 기기 DB 를 보고, 서버 구현은 그 위에 얹혀(`by local` 위임)
  서버에서 받은 것을 기기 DB 에 반영한다. 서버 모드는 기기 DB 파일을 따로 쓴다(`Config.Database.NAME`).

서버 모드 저장소를 고칠 때 지켜야 할 것:

- **화면에 먼저 그려 두지 않는다.** 공감·회수·공지·그룹 변경은 서버가 받아 준 결과만 반영한다. 대화 전송만 예외로, '전송 중'으로 먼저 보이고 결과에 따라 완료·실패로 바뀐다.
- **대화 id 는 앱이 만들고 재전송에도 바꾸지 않는다.** 서버가 같은 id 를 한 번만 받는다.
- **WebSocket 알림만으로 로컬을 채우지 않는다.** 이번 실행에서 서버와 맞춰 본 방에만 알림 대화를 저장한다. 맞춰 보지 않은 방에 끼워 넣으면 그 앞 구간이 영영 빈다.
- **연결될 때마다 다시 조회한다.** 끊겨 있던 동안의 알림은 다시 오지 않는다.

## AI Agent 연동 (AI 요약)

문서 파일 대화의 롱프레스 메뉴 "AI 요약" 이 같은 기기의 AI Agent 앱(별도 저장소)을 링크로 연다. 요약은 그 앱이 하고,
Talk 서버·`:shared` 계약은 바뀌지 않는다(AI Agent 쪽이 사용자 토큰으로 `GET /files/{id}` 를 부른다).

- **링크 계약** — `aiagent://summarize?fileId=<서버 파일 id>&fileName=<파일 이름>&userId=<로그인 아이디>&userName=<이름>`.
  값은 모두 퍼센트 인코딩(공백 `%20`). 받는 쪽이 값을 검증하고 계정을 비교하므로, 이름·순서를 바꾸려면 AI Agent 와 함께 바꾼다. 만드는 곳은 `data/mapper/AiAgentLinkMapper`.
- **흐름** — `ChatActionMenu`(`canRequestAiSummary`) → `ChatRoomActions.OnRequestAiSummary` → `RequestAiSummaryUseCase` →
  `AiSummaryRepository`(파일 id 찾기·링크 열기) → 결과에 따라 `ChatRoomEvent.AiSummaryFileNotReady`·`AiAgentNotInstalled` 토스트.
  두 토스트에는 `TestTags.TOAST_AI_SUMMARY_FILE_NOT_READY`·`TOAST_AI_AGENT_NOT_INSTALLED` 가 붙는다.
- **파일 id** — 말풍선의 `imagePath`·`localPath` 는 기기 캐시 경로라 서버 id 가 아니다. 같은 기기 파일을 여러 방에 보내거나 전달하면 경로 하나를 id 여럿이 가리키므로,
  `ChatRoomRepositoryImpl` 이 서버가 확정한 파일 대화를 반영할 때마다(받은 대화 저장·이전 대화·알림 대화·올리고 보내기·전달) `ServerFileStore.rememberChatFile` 로
  `server_chat_file_<방 id>/<대화 id>` → 파일 id 를 적는다. 유스케이스는 방·대화로 먼저 찾고(`chatFileId`), 그 기록이 없는 옛 대화만 `fileIdOf(path)` 로
  `server_file_<id>` 를 거꾸로 훑는다. 둘 다 없으면 "받는 중" 안내가 뜬다.
- **열기** — `data/local/AppLinkOpener`(Android `ACTION_VIEW`, iOS `canOpenURL`→`openURL`). iOS 는 Info.plist 의 `LSApplicationQueriesSchemes` 에 `aiagent` 가 있어야 한다.
  메뉴는 서버 모드·`Config.ChatRoom.IS_AI_SUMMARY_ENABLED`·`AI_SUMMARY_EXTENSIONS`·회수 안 됨·전송 완료일 때만 보인다.

## 실행과 확인

새 PC 준비는 README 의 "검증 환경 준비" 에 있다. `local.properties` 는 저장소에 없다.

```bash
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleDebug
adb -s emulator-5562 install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
open iosApp/iosApp.xcodeproj   # iOS 는 Xcode 에서 Talk iPhone 17 을 골라 실행
```

- `installDebug` 는 쓰지 않는다. 기기를 지정해서 설치한다.
- **동작 확인은 에뮬레이터·시뮬레이터에서 한다.** 앱 밖으로 인텐트를 쏘는 등 사용자 화면을 점유하는 조작은 하지 않는다.
- **실기기는 서버에 `adb -s <기기> reverse tcp:8090 tcp:8090` 뒤 `localhost:8090` 으로 붙인다.**
- **두 번째 사용자는 `curl` 로 대신할 수 있다.** 다른 계정으로 로그인해 토큰을 받고 REST 를 부르면 에뮬레이터 한 대로도 주고받기·읽음·알림을 확인할 수 있다.
- **로그 검증**은 서버 로그(요청마다 한 줄씩 남는다)와 `adb -s emulator-5562 logcat` 을 함께 본다. `logcat`·`dumpsys` 같은 읽기 전용 조사는 언제든 해도 된다.
- **탭이 먹는지 검증할 때 `adb shell input tap` 을 믿지 않는다.** MOVE 이벤트가 없어서 실제 손가락과 다르게 동작한다.
  부모 레이어가 포인터 이벤트를 소비하면 자식 `clickable` 이 죽는데 `input tap` 으로는 재현되지 않는다. `input motionevent DOWN/MOVE/UP` 을 쓴다.
- **핀치줌·더블탭 같은 멀티터치 제스처는 adb 로 주입하기 어렵다.** 이 경우만 구현·설치까지 하고 사용자에게 확인을 요청한다.
- 스크린샷 픽셀을 직접 읽어 판정하면 UI 상태를 확실하게 검증할 수 있다.

## 주의

- **푸시는 Android 실기기에서만 도착을 확인했다**(테스트 서버, 2026-10-20). Firebase 프로젝트는 `talk-multiplatform` 이다. iOS 는 APNs 키가 Firebase 에 등록되지 않아 아직 오지 않는다.
- 받는 쪽 앱은 파일을 기기 캐시에 내려받아 쓴다.
