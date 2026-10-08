# EzQ_Mobile

Kotlin Multiplatform(Android + iOS) 메신저. 레거시 Android 메신저 `0055_AOS_EZQ8`(자바/MVP)를
KMP + Compose Multiplatform + Clean Architecture 로 리팩토링하는 프로젝트다.

---

## 규칙

1. **메모리 이력이나 프로젝트 구조를 모르는 새 PC에서 처음 실행할 때는, 작업을 시작하기 전에
   전체 소스코드 분석을 먼저 진행해줘.** 이 문서와 `docs/claude/` 를 함께 읽어줘.
2. **앱에 구현된 Clean Architecture 와 MVI 계층을 기억해서, 다른 모듈이나 기능을 추가할 때
   기존 코드 플로우와 동일한 로직으로 구현해줘.** 새 패턴을 임의로 도입하지 말고 기존 화면을
   먼저 찾아 그 구조를 따라줘.
3. **한글을 하드코딩하지 말고 `Res.string` 으로 빼서 한글·영문을 모두 지원하게 해줘.**
   새로 쓰거나 고친 줄에 적용한다. 손대지 않은 기존 하드코딩은 알리기만 한다([작업 방식](#작업-방식) 3).
4. **`//` 로 시작하는 코드 설명 주석은 넣지 마.** 함수나 클래스를 설명하는 `/** */` KDoc 은
   한글로 쓰고 유지해줘. 함수명·변수명은 영어로 써줘.
5. **기능은 Android 와 iOS 양쪽에 모두 적용해줘.** 한쪽만 가능한 기능이면 먼저 가능한지 확인하고
   알려줘. 수정 후에는 양쪽 컴파일을 모두 통과시켜줘.
   ```bash
   ./gradlew :composeApp:compileDebugKotlinAndroid
   ./gradlew :composeApp:compileKotlinIosSimulatorArm64
   ```
6. **서버 응답은 STX + DATA(값이 있는 경우) + ETX 가 모두 들어온 완결 시퀀스일 때만 로컬 DB와
   화면에 반영해줘.** STX 나 ETX 가 누락되면 재요청하고, 최종 실패하면 기존 데이터를 유지해줘
   ([상세](docs/claude/packet-system.md)).
7. **패킷 송신과 응답 처리는 `RepositoryImpl` 에서 해줘.** ViewModel 에서 직접 소켓 응답을
   처리하지 마.
8. **패킷을 보낼 때 `"Android"` 를 하드코딩하지 말고 `Platform.name` 을 써줘.**
9. **코드를 수정한 뒤에는 Android 에뮬레이터와 iOS 시뮬레이터를 띄워서 양쪽 플랫폼에서 실제로
   동작하는지 확인해줘.** 컴파일만 통과시키고 끝내지 마.
10. **로그 검증과 시뮬레이터 테스트 검증이 모두 끝나면 자동으로 커밋하고 푸시해줘.**
    검증이 끝나기 전에는 커밋하지 마.
11. **수정·추가·개선하는 기능마다 브랜치를 새로 따서 진행하고, 10번 검증이 끝나면 머지해줘.**
    브랜치 이름은 작업 내용을 알 수 있게 지어줘.

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
- 일어날 수 없는 경우의 예외 처리는 넣지 않는다. 단, 서버 응답 누락·소켓 끊김·타임아웃은
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
  commonMain/kotlin/org/example/project/
    data/      local · mapper · remote(socket, push, share) · repository
    domain/    model · repository(interface) · usecase
    ui/        도메인별 화면 + uikit(공용 컴포넌트) + theme(AppColors)
    di/        Modules.kt (Koin)
    Config.kt  기능 온오프 · 서버 주소 · 내 정보
    Platform   expect / actual
  androidMain/ Android actual + MainActivity + MyApplication
  iosMain/     iOS actual + MainViewController
iosApp/        Xcode 프로젝트
```

- Clean Architecture(data/domain/ui) + MVI 변형(Action → ViewModel → `StateFlow<UiState>`)
- DI = Koin, 네비게이션 = Voyager, 이미지 = coil3, 소켓 = ktor, DB = SQLDelight
- expect/actual 은 Koin `platformModule` 로 주입한다

## 세부 문서

깊은 내용은 아래에 있다. **해당 영역을 건드리기 전에 먼저 읽을 것.**

| 문서 | 언제 읽나 |
|---|---|
| [아키텍처 · 컨벤션](docs/claude/conventions.md) | ViewModel·페이지네이션·검색·필터·하단 인셋을 다룰 때 |
| [패킷 시스템](docs/claude/packet-system.md) | 서버 통신을 추가·수정할 때 (**fetchPackets 불변식 필독**) |
| [레거시 참조 저장소](docs/claude/legacy-reference.md) | 레거시 동작을 대조할 때 |
| [소켓 재연결 불변식](docs/claude/socket-reconnect.md) | `SocketClient` 연결·재연결 경로를 손댈 때 |
| [대화 송신 복구 불변식](docs/claude/chat-send-recovery.md) | 미확정(SENDING) 대화·재전송을 손댈 때 |
| [안읽음 되덮기](docs/claude/fetchchatrooms-unread-clobber.md) | 안읽음 카운트·읽음 처리를 손댈 때 |
| [안읽음 고정 증상](docs/claude/unread-count-freeze.md) | 말풍선 안읽음이 안 내려간다는 제보를 받을 때 |
| [프로필 사진](docs/claude/profile-photo.md) | 프로필 이미지·FUS 다운로드를 손댈 때 |
| [보안 스켈레톤](docs/claude/security-skeleton.md) | MDM·백신·VPN SDK 를 연동할 때 |
| [로컬 DB 본문·사람 정보 잠그기](docs/claude/local-db-body-encryption.md) | 쪽지·대화 본문이나 사람 정보를 로컬 DB 에 저장할 때 |
| [회수·만료 대화 본문 비우기](docs/claude/chat-body-clearing.md) | 회수·시크릿·시간 제한 대화나 `persist`(대화 재저장)를 손댈 때 |
| [iOS 포그라운드 복귀](docs/claude/ios-foreground-recovery.md) | iOS 복귀 시 화면·소켓 이상을 다룰 때 |
| [앱 전환 화면 스냅샷](docs/claude/app-switcher-snapshot.md) | 최근 앱·앱 전환기 가리기, `FLAG_SECURE`, 앱 활성 전환 시점을 손댈 때 |
| [iOS 푸시 알림](docs/claude/ios-push-notifications.md) | 알림 취소·배지를 손댈 때 |
| [움직이는 이모티콘](docs/claude/animated-emoticon-gif.md) | 이모티콘 렌더링을 손댈 때 |
| [서버 이모티콘](docs/claude/server-emoticon.md) | 이모티콘 목록·다운로드·메시지 형식(`이름\|cate_id\|파일명`)·즐겨찾기·최근 사용·숨김·순서 설정을 손댈 때 |
| [HEIC 사진](docs/claude/heic-photo.md) | 사진 업로드·coil 디코더를 손댈 때, 특정 사진만 회색·PC 에서 안 보인다는 제보를 받을 때 |
| [시스템 폰트 스케일](docs/claude/font-scaling.md) | 글자 크기가 기기 설정을 따라간다는 제보를 받을 때 |
| [Compose 포인터 함정](docs/claude/compose-pointer-consume-trap.md) | 탭이 먹지 않는 증상을 다룰 때 |
| [대화그룹](docs/claude/chatgroup.md) | 대화방 그룹 칩·편집·순서를 손댈 때 |
| [기능 테스트 안전장치](docs/claude/functional-test-guard.md) | 기능 테스트를 만들거나 사람에게 나가는 송신 경로를 추가할 때 |

## 개발 환경 준비

새 머신에서 클론했다면 이것들이 저장소에 없으므로 따로 준비해야 한다.

1. **`local.properties`** — `sdk.dir=<Android SDK 경로>`. gitignore 대상이다.
2. **레거시 참조 저장소** — [레거시 참조 저장소](docs/claude/legacy-reference.md) 의 3개.
   포팅 대조에 필요하다. 체크아웃 경로는 머신마다 다르니 모르면 사용자에게 묻는다.
3. **Zeplin MCP** — 디자인 스펙 연동. 토큰까지 포함해 저장소의 `.mcp.json` 에 등록돼 있으므로
   **Node 20+(`brew install node`)만 있으면 세션 시작 시 자동으로 붙는다.** 별도 설정은 없다.

   Zeplin 짧은 URL(`https://zpl.io/...`)을 주면 화면 스펙·컴포넌트·디자인 토큰·에셋을 읽어
   구현에 반영할 수 있다.

   > 토큰이 저장소에 평문으로 들어 있다. 저장소 접근 권한 = Zeplin 접근 권한이므로,
   > 저장소를 공개로 바꾸거나 외부에 공유할 때는 먼저 토큰을 재발급할 것.

## 검증 방법

규칙 9·10 의 검증을 실제로 수행하는 방법이다.

- **동작 확인은 에뮬레이터·시뮬레이터에서 한다.** 사용자 실기기는 사용자가 붙여 주고 요청했을 때만
  쓴다. 앱 밖으로 인텐트를 쏘는 등 사용자 화면을 점유하는 조작은 하지 않는다.
- **로그 검증**(소켓·재연결·옵션 플래그·패킷)은 반복 실행해 확인한다.
  `adb logcat` · `dumpsys` · `pm query-activities` 같은 읽기 전용 조사는 언제든 해도 된다.
- **탭이 먹는지 검증할 때 `adb shell input tap` 을 믿지 말 것.** MOVE 이벤트가 없어서 실제 손가락과
  다르게 동작한다. `input motionevent DOWN/MOVE/UP` 을 쓴다
  ([상세](docs/claude/compose-pointer-consume-trap.md)).
- **핀치줌·더블탭 같은 멀티터치 제스처는 adb 로 주입하기 어렵다.** 이 경우만 구현·설치까지 하고
  사용자에게 확인을 요청한다.
- 스크린샷 픽셀을 직접 읽어 판정하면 UI 상태를 확실하게 검증할 수 있다.
- 서버(`Config.Socket.IP`)는 실제 운영 서버다. 메시지 발송처럼 외부로 나가는 동작은 검증
  시나리오에 넣기 전에 영향 범위를 확인한다.
