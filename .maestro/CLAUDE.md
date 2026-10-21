# 실화면 흐름(Maestro) — Talk_Multiplatform

루트 `CLAUDE.md` 의 "실화면 흐름" 절에 이어지는 세부다. 루트의 규칙이 먼저다. 경로는 저장소 루트 기준이다.

- **기기 고르기·켜기·앱 설치·Maestro 실행은 `.maestro/run.sh` 가 한다.** Gradle 작업은 APK 를 만들고 로컬 서버를 띄운 뒤
  이 스크립트를 부른다. 서버가 떠 있으면 스크립트만 따로 돌려도 된다(`.maestro/run.sh android <apk>`, `.maestro/run.sh ios`).
  결과는 `build/maestro/<플랫폼>/report.xml`(JUnit)과 같은 폴더의 스크린샷·로그다.
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
