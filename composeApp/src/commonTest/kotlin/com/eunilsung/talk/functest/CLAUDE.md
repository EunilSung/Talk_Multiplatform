# 기능 테스트 — Talk_Multiplatform

루트 `CLAUDE.md` 의 규칙이 먼저다. 경로는 저장소 루트 기준이다.

앱이 실제로 쓰는 통신 코드(`TalkServerClient`·`TalkSocket`)와 저장소로 진짜 서버에 붙어, 양쪽 대역이 놓치는 계약 어긋남을 잡는다.

```bash
./gradlew :composeApp:testDebugUnitTest -PfunctionalTest=true --tests "*functest*"
```

- **`-PfunctionalTest=true` 가 없으면 돌지 않는다.** 평소 `testDebugUnitTest` 에서는 서버로 아무것도 나가지 않고 통과한다.
- **Android Studio 에서는 실행 구성 `기능테스트 (실 서버)` 를 고르고 실행 버튼을 누르면 된다**(`.run` 폴더).
  서버 없이 도는 테스트만 돌리려면 `테스트 (서버 없이)` 를 고른다.
- **로컬 서버는 알아서 뜬다.** 떠 있지 않으면 빌드가 띄웠다가 끝날 때 내리고(로그는
  `server/build/local-server/server.log`), 이미 떠 있으면 그대로 쓰고 건드리지 않는다. 다른 서버에
  돌리려면 `-PfunctionalTestUrl=<주소>` 를 준다.
- **결과는 `[FT]` 로그로 본다.** `composeApp/build/test-results/testDebugUnitTest/*functest*.xml` 에서
  `STEP-FAIL` 을 찾으면 어느 시나리오의 어느 단계에서 무엇이 달랐는지 나온다. 단계 밖(준비)에서 깨지면
  `SCENARIO-ERROR` 로 남는다. 콘솔은 한글이 깨져 보일 수 있다.
- **묶음** — 로그인 · 대화방 목록 · 대화 · 읽음 · 공감/회수/공지/책갈피 · 파일 · 투표 · 대화그룹 · 사람 ·
  초대 · 전달 · AI, 그리고 갈래들(`DetailScenarioTest`: 이모티콘·사진·둘만의 방·나와의 대화·그룹 순서·투표 옵션·
  AI 의 나머지 방식). 새 기능을 서버에 붙였으면 해당 묶음에 시나리오를 더한다.
- **화면 상태는 ViewModel 묶음으로 본다**(`*ViewModelScenarioTest` — 대화방·대화함·로그인·투표·초대·내 그룹·
  프로필). 화면이 하는 일을 액션으로 넣고 상태와 한 번만 알리는 일(`events`)이 진짜 서버의 응답으로 맞게
  만들어지는지 본다. ViewModel 은 `FunctionalViewModels.kt` 의 `harness.xViewModel()` 로 세우고, 메인 스레드
  대역은 한 번에 하나만 도는 `viewModelMainThread` 를 쓴다. 버튼을 실제로 누르지는 않는다 — 버튼과 액션의
  연결은 에뮬레이터에서 확인한다.
- **둘만의 방과 나와의 대화방에는 아무것도 보내지 않는다.** 계정에 하나뿐이라 사람이 쓰던 방일 수 있고,
  같은 상대와의 방은 서버가 있던 방을 돌려주므로 지난 실행의 대화가 섞인다. 찾기만 하고 나가지도 않는다.
  시나리오는 `harness.newRoom` 으로 매번 새 단체방을 만들고, 끝나면 모두 나간다.
- **AI 결과는 몇 번 다시 묻는다**(`aiResult`). 모델 제공처가 분당 한도로 잠깐씩 거절하기 때문이다.
  한 번 돌릴 때 모델을 열 번쯤 부른다.
- **테스트 계정끼리만 주고받는다.** 러너는 로그인한 계정이 `test1`~`test10` 이 아니면 실행을 거부한다.
  시나리오가 상대로 쓰는 계정도 그 안에서만 고른다.
- **상대는 서버 호출로만 움직인다.** 내 정보(`Config.MyInfo`)가 프로세스에 하나라 앱을 둘 세울 수 없다.
- **AI 단계는 건너뛸 수 있다.** 키가 없는 서버나 사용량 한도(사람당 시간당 20회)에 걸린 서버에서는
  `STEP-SKIP` 으로 남고 실패가 아니다. 그래서 키 없이 돌린 검증이 초록이어도 AI 는 확인되지 않은 것이다.
  AI 를 확인할 때는 서버를 띄우는 환경에 `GEMINI_API_KEY` 를 두고 `-PrequireAi=true` 를 준다 — 건너뜀을 실패로 세고
  실화면 AI 흐름까지 돈다. 검증 빌드가 띄우는 로컬 서버는 AI 한도를 사람당 시간당 200회로 준다(`AI_PER_USER_HOURLY`).
- 배포한 서버에 돌리면 테스트 계정으로 방·대화·파일이 실제로 만들어진다. 방에서는 나가지만 대화와
  파일은 서버에 남는다.
