# 배포 — Talk_Multiplatform

루트 `CLAUDE.md` 의 규칙이 먼저다. 경로는 저장소 루트 기준이다.

운영 구성은 `deploy/` 에 있다 — PostgreSQL + 서버 + Caddy(HTTPS)를 Docker Compose 로 띄운다.

```bash
DEPLOY_HOST=<SSH 사용자>@<테스트 서버 공인 IP> DEPLOY_KEY=~/.ssh/<키 파일> deploy/deploy.sh   # 빌드 → 올리기 → 다시 띄우기 → /health 확인
```

- **실제 주소·SSH 사용자·키 파일 이름은 저장소에 적지 않는다.** 이 저장소는 공개라 문서에 적힌 값은 누구나 본다.
  실제 값은 저장소 밖 노트(`../Info/Talk_info/테스트서버.md`)에 있다. 이 문서와 명령에는 `<테스트 서버 주소>` 같은 자리표시만 쓴다.
  주소는 앱과 인증서 기록으로도 드러나는 값이라 숨기는 것을 막는 수단으로 보지 않는다. 계정·한도로 막는다.
- **테스트 서버** — Oracle Cloud Always Free 인스턴스(`VM.Standard.E2.1.Micro`, Ubuntu 24.04 Minimal) 하나다.
  주소는 `https://<테스트 서버 주소>`(도메인이 없어 `<공인 IP>.sslip.io`). 같은 클라우드 계정의 다른 인스턴스는 다른 프로젝트의
  것이니 건드리지 않는다(이름은 `../Info/Talk_info/테스트서버.md`).
- **Gradle 빌드는 배포하는 PC 에서 한다.** 인스턴스 메모리가 1GB 라 서버에서는 실행본(`installDist`)을 담은 이미지만
  만든다. JVM 힙(256MB)·DB 커넥션(5)·Postgres 메모리를 작게 잡았고 스왑 2GB 를 둔다. 메모리가 모자라면 JVM 이 끝나고
  (`-XX:+ExitOnOutOfMemoryError`) compose 가 다시 띄운다.
- **테스트 서버는 AI(Gemini)·푸시(Firebase)가 켜져 있다.** AI 한도는 운영 기본값(사람당 시간당 20회)이다.
- **비밀값은 서버의 `~/talk/.env` 에만 있다**(`deploy/.env.example` 참고). `deploy.sh` 는 이 파일을 건드리지 않는다.
  AI·푸시 키를 넣으려면 `.env` 를 고치고 `docker compose up -d` 를 다시 한다.
- **시연용 계정(test1~test10)은 `.env` 에 `SEED_DEMO_ACCOUNTS=true` 와 `SEED_PASSWORD` 가 함께 있을 때만 만든다.**
  기본 비밀번호는 없다 — 저장소·앱 문구에 적힌 로컬용 비밀번호(`1234`)는 로컬 개발 서버(`:server:runLocal`)만 쓴다.
  `SEED_PASSWORD` 는 `openssl rand -hex 12` 로 만들고, 다시 띄우면 이미 있는 시연용 계정의 비밀번호도 그 값으로 바뀐다.
  비밀번호가 바뀐 계정은 예전 비밀번호로 받은 로그인(토큰)도 모두 끊긴다 — 기기마다 새 비밀번호로 다시 로그인한다.
  둘 중 하나라도 없으면 계정을 만들지 않고, 예전 계정이 남아 있으면 서버 로그에 경고가 남는다.
- **한도** — JSON 본문 256KB(Caddy 는 1MB), 파일 올리기 20MB(Caddy 25MB), 사람당 하루 파일 200개·500MB(전달로 복사한 것 포함),
  사람당 동시 올리기 3개·한 건 10분, 단체방 만들기는 사람당 분당 30회·하루 300회, 초대는 분당 30회·하루 500회, 투표 만들기는 분당 30회·하루 300회, 방 인원 300명, 알림 연결은 사람당 5개, 푸시 토큰은 로그인 하나에 하나·사람당 10개.
  값은 `server/.../limits/ServerLimits.kt` 에 있다.
- **GitHub Actions 도 같은 `deploy.sh` 로 배포한다.** `v*` 태그 → `Release`(릴리스 파일: `DEPLOY_STAGE_ONLY=1` 로 만든 서버 묶음) →
  `Deploy`(그 묶음을 받아 `DEPLOY_STAGE`·`DEPLOY_HOST_KEY_CHECK=yes` 로 올린다). 접속 정보는 `test-server` 환경 비밀값에만 두고,
  넣는 순서와 실제 값은 `../Info/Talk_info/CI_설정.md` 에 있다. 서버 `.env` 의 값은 CI 에 넣지 않는다.
- **새 인스턴스는 한 번 준비한다** — `sudo bash setup-host.sh`(스왑, Docker, iptables 80·443, DB 백업 cron).
  OCI 의 Ubuntu 이미지는 iptables 가 22 번 말고 모두 막는다. VCN 보안 목록에도 80·443 이 열려 있어야 한다.
- **DB 백업** — 매일 03:30(UTC) `~/backups` 에 7개까지 남는다.
- **도메인이 없어 `sslip.io` 를 쓴다.** 공인 IP 가 바뀌면 `.env` 의 `DOMAIN` 과 앱의 `server.baseUrl` 을 함께 바꾼다.
- 서버는 1/8 OCPU 라 뜨는 데 40초쯤 걸린다(시연용 계정을 켜면 비밀번호 확인 10번만큼 더 걸린다). 그동안 Caddy 는 502 를 준다.
- **배포한 서버 확인** — 기능 테스트는 `-PfunctionalTestUrl=https://<테스트 서버 주소>`, 실화면 흐름은 앱을 그 주소로
  빌드해 `TALK_SERVER_BASE_URL=<주소> .maestro/run.sh android <apk>`(또는 `ios`). `peer.js` 도 같은 주소로 움직인다.
  배포한 서버의 시연용 비밀번호는 `SEED_PASSWORD` 라 로컬용 비밀번호로는 로그인되지 않는다. 실제 명령은 `../Info/Talk_info/테스트서버.md` 에 있다.
