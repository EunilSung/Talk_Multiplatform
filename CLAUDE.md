# Talk_Multiplatform

Android·iOS 메신저 앱과 Ktor 서버가 한 저장소에 있다. 기본 브랜치는 `main` 이다.

## 모듈

| 폴더 | 역할 | 세부 문서 |
|---|---|---|
| `composeApp/` | 앱(공용 코드, Android·iOS 구현) | `composeApp/CLAUDE.md` |
| `shared/` | 앱·서버 공용 요청/응답 모델 | - |
| `server/` | Ktor 서버 | `server/CLAUDE.md` |
| `iosApp/` | Xcode 프로젝트 | - |
| `deploy/` | 배포·테스트 서버 | `deploy/CLAUDE.md` |
| `.maestro/` | 실화면 흐름 | `.maestro/CLAUDE.md` |

기능 테스트 세부는 `composeApp/src/commonTest/kotlin/com/eunilsung/talk/functest/CLAUDE.md` 에 있다.

## 앱·서버 경계

- 앱과 서버의 계약을 바꿀 때는 `:shared` 모델을 먼저 고치고 양쪽을 같이 맞춘다. 한쪽만 바꾸면 런타임에서만 깨진다.
- 앱 테스트는 서버 대역을, 서버 테스트는 클라이언트 대역을 쓴다. 서버나 계약을 바꿨으면 기능 테스트를 켜고 돌린다.
- AI Agent 앱(별도 저장소)과 링크 계약이 맞물려 있다. 세부는 `composeApp/CLAUDE.md` 에 있다.

## 규칙

- 요구사항 해석이 갈리거나, 되돌리기 어려운 결정(서버·사람에게 나가는 동작, 데이터 삭제, DB 스키마 변경)이면 멈추고 묻는다.
  구현 세부는 기존 코드를 따라 진행한 뒤 무엇을 가정했는지 알린다.
- Kotlin 코드에 `//` 설명 주석을 넣지 않는다. `/** */` KDoc 은 한글로 쓰고 유지한다. 함수명·변수명은 영어로 쓴다.
- 수정·추가·개선하는 기능마다 브랜치를 새로 딴다. 이름은 작업 내용을 알 수 있게 짓는다.
- 커밋·머지·푸시는 사용자가 요청할 때만 한다.
- 배포와 `v*` 태그 푸시는 사용자가 "배포해줘" 라고 한 뒤에만 한다.

## 완료 기준

- 고칠 때마다 서버·앱·기능 테스트를 돌린다. 실행 구성 `검증 - 빠르게 (JVM)` 과 같다.
  ```bash
  ./gradlew :server:test :composeApp:testDebugUnitTest -PfunctionalTest=true --continue
  ```
- 화면을 바꿨으면 에뮬레이터와 시뮬레이터에서 직접 확인한다.
- 머지 전에는 전체 검증을 돌린다. 8분쯤 걸린다. 실행 구성 `검증 - 전체 (실화면 포함)` 과 같다.
  ```bash
  TALK_SERVER_BASE_URL=http://localhost:8090 ./gradlew :composeApp:verifyAll -PfunctionalTest=true --continue
  ```
- 푸시의 실제 도착, 핀치줌 같은 멀티터치, 카메라, iOS 한글 입력은 자동으로 확인되지 않는다. AI 단계는 키가 없는 서버에서 건너뛴다.
  이것들을 고쳤으면 손으로 확인하거나, 확인하지 못했다고 알린다.

## 실행

```bash
./gradlew :server:runLocal   # 로컬 서버(포트 8090) + 내장 PostgreSQL(포트 54329)
```

로컬 서버는 시연용 계정 `test1`~`test10` / `1234` 를 만들어 둔다.

## 기기

- Talk 전용은 `Talk_Phone`(`emulator-5562`)과 `Talk iPhone 17` 이다. 전체 검증은 `local.properties` 의 `e2e.*` 로 이 기기를 고른다.
- 사용자 실기기는 사용자가 붙여 주고 요청했을 때만 쓴다.

## 공개 저장소

- 이 저장소는 공개다. 실제 서버 주소·SSH 사용자·키 파일 이름·비밀값을 저장소에 적지 않는다.
- 실제 값은 저장소 밖 `../Info/Talk_info/` 에 있다. 문서와 명령에는 `<테스트 서버 주소>` 같은 자리표시만 쓴다.
