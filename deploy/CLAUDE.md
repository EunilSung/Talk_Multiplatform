# 배포 — Talk_Multiplatform

루트 `CLAUDE.md` 의 규칙이 먼저다. 경로는 저장소 루트 기준이다.

운영 구성은 `deploy/` 에 있다 — PostgreSQL + 서버 + Caddy(HTTPS)를 Docker Compose 로 띄운다.

```bash
DEPLOY_HOST=ubuntu@168.110.2.222 deploy/deploy.sh   # 빌드 → 올리기 → 다시 띄우기 → /health 확인
```

- **테스트 서버** — Oracle Cloud 도쿄 `test-server`(`VM.Standard.E2.1.Micro`, Always Free, Ubuntu 24.04 Minimal).
  주소는 `https://168.110.2.222.sslip.io`, SSH 키는 `~/.ssh/oracle_test_server`. 같은 계정의 `gnt-server` 는 다른
  프로젝트의 것이니 건드리지 않는다.
- **Gradle 빌드는 배포하는 PC 에서 한다.** 인스턴스 메모리가 1GB 라 서버에서는 실행본(`installDist`)을 담은 이미지만
  만든다. JVM 힙(256MB)·DB 커넥션(5)·Postgres 메모리를 작게 잡았고 스왑 2GB 를 둔다.
- **테스트 서버는 AI(Gemini)·푸시(Firebase `talk-multiplatform`)가 켜져 있다.** AI 한도는 운영 기본값(사람당 시간당 20회)이다.
- **비밀값은 서버의 `~/talk/.env` 에만 있다**(`deploy/.env.example` 참고). `deploy.sh` 는 이 파일을 건드리지 않는다.
  AI·푸시 키를 넣으려면 `.env` 를 고치고 `docker compose up -d` 를 다시 한다.
- **새 인스턴스는 한 번 준비한다** — `sudo bash setup-host.sh`(스왑, Docker, iptables 80·443, DB 백업 cron).
  OCI 의 Ubuntu 이미지는 iptables 가 22 번 말고 모두 막는다. VCN 보안 목록에도 80·443 이 열려 있어야 한다.
- **DB 백업** — 매일 03:30(UTC) `~/backups` 에 7개까지 남는다.
- **도메인이 없어 `sslip.io` 를 쓴다.** 공인 IP 가 바뀌면 `.env` 의 `DOMAIN` 과 앱의 `server.baseUrl` 을 함께 바꾼다.
- 서버는 1/8 OCPU 라 뜨는 데 40초쯤 걸린다. 그동안 Caddy 는 502 를 준다.
- **배포한 서버 확인** — 기능 테스트는 `-PfunctionalTestUrl=https://168.110.2.222.sslip.io`, 실화면 흐름은 앱을 그 주소로
  빌드해 `TALK_SERVER_BASE_URL=<주소> .maestro/run.sh android <apk>`(또는 `ios`). `peer.js` 도 같은 주소로 움직인다.
