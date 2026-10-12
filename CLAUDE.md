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
```

- Clean Architecture(data/domain/ui) + MVI 변형(Action → ViewModel → `StateFlow<UiState>`)
- DI = Koin, 네비게이션 = Voyager, 이미지 = coil3, 통신 = ktor, 기기 DB = SQLDelight
- 의존성은 안쪽으로만 흐른다. `domain` 은 `ui`·`data` 를 모르고 Compose 타입도 쓰지 않는다

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
- 안읽음 수·득표수처럼 계산할 수 있는 값은 저장하지 않는다.
- 바꾸는 요청은 전부 REST 로 받고, WebSocket 은 "무엇이 바뀌었다"를 알리는 데만 쓴다.
- 서버 테스트는 내장 PostgreSQL 로 실제 쿼리를 돌린다. 설치할 것이 없다.

## 개발 환경 준비

새 머신에서 클론했다면 이것들이 저장소에 없으므로 따로 준비해야 한다.

1. **JDK 17** — Gradle 과 서버가 17 을 쓴다. PATH 의 java 가 더 낮으면 `JAVA_HOME` 을 17 로 잡는다.
2. **`local.properties`** — gitignore 대상이다.
   ```properties
   sdk.dir=<Android SDK 경로>
   # 서버 모드로 빌드할 때만. 비우면 로컬 모드다.
   # Android 에뮬레이터: http://10.0.2.2:8090 / iOS 시뮬레이터: http://localhost:8090
   server.baseUrl=http://10.0.2.2:8090
   ```
3. **Firebase 서비스 계정 키** — 서버가 푸시를 보낼 때만 필요하다. 저장소에 없으니 Firebase 콘솔
   (`multiplatformtalk` 프로젝트)에서 받아 `FIREBASE_CREDENTIALS=<파일 경로>` 로 넘긴다.
   없으면 푸시만 꺼지고 나머지는 그대로 동작한다.
4. **Gemini API 키** — AI 참여자·말풍선 번역·글 다듬기에만 필요하다. Google AI Studio 에서 받아 서버를 띄우는
   환경에 `GEMINI_API_KEY` 로 넘긴다(모델은 `AI_MODEL`, 기본 `gemini-3.5-flash-lite`).
   없으면 AI 만 꺼지고 나머지는 그대로 동작한다.

## 실행

```bash
./gradlew :server:runLocal             # 로컬 서버 (포트 8090) + 내장 PostgreSQL (포트 54329)
./gradlew :composeApp:installDebug     # Android 설치
open iosApp/iosApp.xcodeproj           # iOS 는 Xcode 에서 시뮬레이터 선택 후 실행
```

- 로컬 서버의 데이터는 `server/.localdb`, 올린 파일은 `server/.localfiles` 에 남는다. 지우면 처음부터다.
- 서버 주소는 빌드할 때 상수로 박힌다. `server.baseUrl` 을 바꿨으면 앱을 다시 빌드해야 한다.
- 운영 진입점(`:server:run`)은 `DB_URL`·`DB_USER`·`DB_PASSWORD`·`FILES_DIR`·`PORT` 환경변수를 읽는다.
  `SERVER_ONLY=1` 을 주면 앱 모듈을 빼고 서버만 구성한다(배포용).
- 로그인은 테스트 계정 `test1`~`test10` / `1234` 를 쓴다. 서버가 처음 뜰 때 만들어 둔다.

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

- **Android Studio 에서는 실행 구성 `기능테스트 (실 서버)` 를 고르고 실행 버튼을 누르면 된다**(`.run` 폴더).
  서버 없이 도는 테스트만 돌리려면 `테스트 (서버 없이)` 를 고른다.
- **로컬 서버는 알아서 뜬다.** 떠 있지 않으면 빌드가 띄웠다가 끝날 때 내리고(로그는
  `server/build/local-server/server.log`), 이미 떠 있으면 그대로 쓰고 건드리지 않는다. 다른 서버에
  돌리려면 `-PfunctionalTestUrl=<주소>` 를 준다.
- **`-PfunctionalTest=true` 가 없으면 돌지 않는다.** 평소 `testDebugUnitTest` 에서는 서버로 아무것도 나가지
  않고 통과한다. 서버를 건드렸거나 `:shared` 의 계약을 바꿨으면 켜고 돌린다.
- **결과는 `[FT]` 로그로 본다.** `composeApp/build/test-results/testDebugUnitTest/*functest*.xml` 에서
  `STEP-FAIL` 을 찾으면 어느 시나리오의 어느 단계에서 무엇이 달랐는지 나온다. 단계 밖(준비)에서 깨지면
  `SCENARIO-ERROR` 로 남는다. 콘솔은 한글이 깨져 보일 수 있다.
- **묶음** — 로그인 · 대화방 목록 · 대화 · 읽음 · 공감/회수/공지/책갈피 · 파일 · 투표 · 대화그룹 · 사람 ·
  초대 · AI, 그리고 갈래들(`DetailScenarioTest`: 이모티콘·사진·둘만의 방·나와의 대화·그룹 순서·투표 옵션·
  AI 의 나머지 방식). 새 기능을 서버에 붙였으면 해당 묶음에 시나리오를 더한다.
- **화면 상태는 ViewModel 묶음으로 본다**(`*ViewModelScenarioTest` — 대화방·대화함·로그인·투표·초대·내 그룹·
  프로필). 화면이 하는 일을 액션으로 넣고 상태와 한 번만 알리는 일(`events`)이 진짜 서버의 응답으로 맞게
  만들어지는지 본다. ViewModel 은 `FunctionalViewModels.kt` 의 `harness.xViewModel()` 로 세우고, 메인 스레드
  대역은 한 번에 하나만 도는 `viewModelMainThread` 를 쓴다. 버튼을 실제로 누르지는 않는다 — 버튼과 액션의
  연결은 에뮬레이터에서 확인한다.
- **둘만의 방과 나와의 대화방에는 아무것도 보내지 않는다.** 계정에 하나뿐이라 사람이 쓰던 방일 수 있다.
  찾기만 하고 나가지도 않는다.
- **AI 결과는 몇 번 다시 묻는다**(`aiResult`). 모델 제공처가 분당 한도로 잠깐씩 거절하기 때문이다.
  한 번 돌릴 때 모델을 열 번쯤 부른다.
- **테스트 계정끼리만 주고받는다.** 러너는 로그인한 계정이 `test1`~`test10` 이 아니면 실행을 거부한다.
  시나리오가 상대로 쓰는 계정도 그 안에서만 고른다.
- **둘만의 방을 쓰지 않는다.** 같은 상대와의 방은 서버가 있던 방을 돌려주므로 지난 실행의 대화가
  섞인다. `harness.newRoom` 으로 매번 새 단체방을 만들고, 끝나면 모두 나간다.
- **상대는 서버 호출로만 움직인다.** 내 정보(`Config.MyInfo`)가 프로세스에 하나라 앱을 둘 세울 수 없다.
- **AI 단계는 건너뛸 수 있다.** 키가 없는 서버나 사용량 한도(사람당 시간당 20회)에 걸린 서버에서는
  `STEP-SKIP` 으로 남고 실패가 아니다.
- 배포한 서버에 돌리면 테스트 계정으로 방·대화·파일이 실제로 만들어진다. 방에서는 나가지만 대화와
  파일은 서버에 남는다.

## 주의

- **로컬 서버 포트는 8090 이다.** 운영 기본값 8080 을 피한 것으로, 같은 PC 에서 다른 프로젝트의
  서버와 겹치지 않게 하려는 것이다.
- **파일 하나는 20MB 까지다**(`MAX_FILE_BYTES`). 받는 쪽 앱은 파일을 기기 캐시에 내려받아 쓴다.
- **푸시는 실제 발송을 확인하지 못했다.** 토큰 등록과 누구에게 보낼지의 판단까지만 테스트돼 있다.
  서비스 계정 키를 넣고 실기기에서 확인해야 한다. iOS 는 APNs 키도 Firebase 에 등록돼 있어야 한다.
- **배포 구성(Docker·Caddy·배포 스크립트)은 아직 없다.**
