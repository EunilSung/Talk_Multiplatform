# 실화면 흐름(Maestro) — Talk_Multiplatform

루트 `CLAUDE.md` 의 규칙이 먼저다. 경로는 저장소 루트 기준이다.

기능 테스트가 비워 둔 "버튼과 액션의 연결"을 에뮬레이터·시뮬레이터에서 실제로 눌러 본다. 같은 흐름 파일을 Android 와 iOS 에 그대로 돌린다.

```bash
TALK_SERVER_BASE_URL=http://localhost:8090 ./gradlew :composeApp:e2eAndroid   # 또는 :composeApp:e2eIos
```

## 요소 찾기

- **요소는 `TestTags`(`ui/uikit/TestTags.kt`)의 이름을 `id` 로 찾는다.** 화면 문구는 기기 언어마다 다르고 같은 문구가 여러 곳에 나온다.
  흐름이 누르는 요소를 새로 만들면 태그를 붙이고 흐름 파일에도 같은 이름을 쓴다. 상대가 보낸 말·방 이름처럼 흐름이 만든 영문 글자는 글자로 찾아도 된다.
- **다이얼로그는 Surface 에 `exposeTestTags()` 를 붙인다.** Android 는 다이얼로그가 창이 따로라 앱 루트의 설정이 닿지 않는다. iOS 는 testTag 가 그대로 접근성 식별자로 나간다.
- **흐름이 확인하는 토스트는 `ToastManager.show(…, testTag = TestTags.X)` 로 띄운다.** 토스트 문구도 앱 문자열이라 글자로 찾지 않는다. 안내가 여럿이면 태그를 따로 둔다.

## 전체 검증

`verifyAll` 의 순서: 서버 테스트 → 로컬 서버 띄우기 → 앱 테스트와 기능 테스트(JVM) → 기능 테스트(iOS 시뮬레이터) → Android 앱 빌드·설치·실화면 흐름 → iOS 앱 빌드·설치·실화면 흐름.
띄운 서버는 끝나면 내려간다. 하나가 깨져도 끝까지 돌고(`--continue`), 깨진 단계가 마지막에 모여 나온다.

- **기기는 `local.properties` 의 `e2e.androidAvd`·`e2e.iosDevice` 로 고른다.** iOS 기능 테스트도 같은 시뮬레이터에서 돈다.
  iOS 는 없으면 켜져 있는 iPhone 시뮬레이터 중 첫 번째를 쓰고, 실기기는 고르지 않는다.
- **설정이 맞지 않으면 시작하자마자 멈춘다.** 기능 테스트가 꺼져 있거나(`-PfunctionalTest=true` 없음) 앱이 로컬 서버가 아닌 주소로 빌드되면 처음에 이유를 알려 준다.
- 처음 iOS 앱을 빌드할 때는 패키지를 받느라 더 걸린다.

## 실행 세부

- **기기 고르기·켜기·앱 설치·Maestro 실행은 `.maestro/run.sh` 가 한다.** Gradle 작업은 APK 를 만들고 로컬 서버를 띄운 뒤
  이 스크립트를 부른다. 서버가 떠 있으면 스크립트만 따로 돌려도 된다(`.maestro/run.sh android <apk>`, `.maestro/run.sh ios`).
  결과는 `build/maestro/<플랫폼>/report.xml`(JUnit)과 같은 폴더의 스크린샷·로그다.
- **Android 는 `e2e.androidAvd` 로 이름을 준 에뮬레이터에만 깐다.** 켜져 있는 첫 에뮬레이터를 고르지 않는다 — 같은 PC 의 다른 작업이
  쓰는 기기일 수 있다. 켤 때는 `e2e.androidPort`(또는 `TALK_ANDROID_PORT`)의 포트를 쓰고, 그 포트를 다른 에뮬레이터가 쓰고 있으면 멈춘다.
  스크립트의 `adb` 는 모두 `-s <기기>` 로 대상을 붙이고, adb 서버를 다시 띄우지 않는다(다른 작업의 연결도 끊긴다).
- **앱은 로컬 서버 주소(`http://localhost:8090`)로 빌드한다.** `adb reverse` 를 걸면 Android 도 같은 주소를 쓰므로
  플랫폼마다 `local.properties` 를 바꿔 가며 빌드하지 않아도 된다. `TALK_SERVER_BASE_URL` 이 `server.baseUrl` 보다 먼저라
  평소 설정을 건드리지 않는다. 기기는 지정해서 설치한다(`installDebug` 금지).
- **상대는 `scripts/peer.js` 가 REST 로 움직인다.** 흐름마다 test2 가 test1·test3 와 새 단체방(`E2E-<시각>`)을 만들고
  말을 건다. 앱에서 한 일이 서버에 남았는지도 같은 스크립트로 확인하고, 흐름이 끝나면(`onFlowComplete`) 셋 다 나간다.
- **Android 의 `hideKeyboard` 는 뒤로 가기를 누른다.** 키보드가 떠 있을 때만 쓴다. 떠 있지 않으면 화면이 닫힌다.
- **입력은 영문·숫자로 한다.** iOS 시뮬레이터에서 한글 입력이 안정적이지 않다.
- **로그인 입력칸은 1초 동안 미끄러져 올라온다.** 도중에 넣은 글자는 사라질 수 있어 `launch.yaml` 이 다 멈출 때까지 기다린다.
- **태블릿 AVD(`MPT_Tablet`)는 키보드 높이를 앱에 알리지 않는다.** 키보드가 입력바·버튼을 가려 대화·투표 흐름이 깨진다.
  `e2e.androidAvd` 는 폰 AVD 로 고른다. 로그인 버튼은 Android 에서 키보드를 내린 뒤 누른다(`login.yaml`).
- **`ai` 이름표가 붙은 흐름(번역·다듬기)은 `-PrequireAi=true` 일 때만 돈다.** 키가 없는 서버에서는 뜻이 없어 평소에는 뺀다.
- **AI 요약 흐름(`06_ai_summary.yaml`)은 기기에 AI Agent 앱이 있는지에 따라 확인할 것이 다르다.** `run.sh` 가 기기에서
  `com.eunilsung.aiagent` 를 찾아 `-e AI_AGENT_INSTALLED=true|false` 로 넘기고, `TALK_E2E_AI_AGENT_INSTALLED` 로 직접 정할 수도
  있다. 값이 없으면 없는 쪽이다.
  - 없으면 설치 안내 토스트를 `id: toast_ai_agent_not_installed` 로 기다린다. 토스트는 문구 대신 태그로 찾는다 — "받는 중"
    토스트(`toast_ai_summary_file_not_ready`)와 구분되고, 문구를 바꾸거나 번역해도 흐름이 깨지지 않는다.
  - 있으면 두 가지를 본다. Talk 에만 있는 `chat_room_menu` 가 사라지는지, 그리고 AI Agent 에만 있는 요소(같은 계정이면 새 대화방의
    `chat_back`, 다른 계정·로그인 안 됨이면 안내 팝업의 `dialog_title`)가 보이는지. `chat_input` 은 AI Agent 대화방에도 같은 이름이
    있어 앱이 바뀌었는지 판정하는 데 쓰지 않는다. AI Agent 쪽 이름은 그 저장소의 `ui/uikit/TestTags.kt` 와 맞춘다.
  - 파일 id 를 아직 모르면(기록을 남기기 전에 받은 대화) "받는 중" 안내가 떠서 몇 번 다시 누른다(`retry`).
