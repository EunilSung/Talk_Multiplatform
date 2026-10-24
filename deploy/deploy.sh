#!/usr/bin/env bash
# 서버를 빌드해 인스턴스에 올리고 다시 띄운다. 배포하는 PC 에서 돌린다.
#   DEPLOY_HOST=<SSH 사용자>@<공인IP> DEPLOY_KEY=~/.ssh/<키 파일> deploy/deploy.sh
# DEPLOY_KEY 를 비우면 ssh 의 기본 키(~/.ssh/config 포함)를 쓴다.
# 서버의 ~/talk/.env 는 건드리지 않는다. 처음 한 번은 setup-host.sh 와 .env 준비가 먼저다.
# GitHub Actions(.github/workflows)가 함께 쓰는 값:
#   DEPLOY_STAGE_ONLY=1        올릴 묶음(build/deploy)만 만들고 끝낸다 — 릴리스 파일을 만들 때
#   DEPLOY_STAGE=<폴더>        빌드하지 않고 이미 만든 묶음을 올린다 — 릴리스 파일을 배포할 때
#   DEPLOY_HOST_KEY_CHECK=yes  known_hosts 에 있는 호스트 키만 믿는다. 기본 accept-new 는 처음 보는 키를 받아들인다
#   DEPLOY_SHOW_LOGS=0         실패해도 서버 로그를 찍지 않는다 — 공개 Actions 로그에서 돌릴 때
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
stage="${DEPLOY_STAGE:-$root/build/deploy}"
cd "$root"

if [ -z "${DEPLOY_STAGE:-}" ]; then
  SERVER_ONLY=1 ./gradlew :server:installDist --quiet
  rm -rf "$stage"
  mkdir -p "$stage"
  cp deploy/Dockerfile deploy/docker-compose.yml deploy/Caddyfile deploy/.env.example \
     deploy/setup-host.sh deploy/backup.sh "$stage/"
  cp -R server/build/install/server "$stage/server"
fi
if [ -n "${DEPLOY_STAGE_ONLY:-}" ]; then
  echo "배포 묶음: $stage"
  exit 0
fi

host="${DEPLOY_HOST:?DEPLOY_HOST=<SSH 사용자>@<공인IP> 를 준다}"
case "${DEPLOY_KEY:-}" in *[[:space:]\'\"]*) echo "DEPLOY_KEY 경로에 공백·따옴표를 넣지 않는다" >&2; exit 1 ;; esac
case "${DEPLOY_HOST_KEY_CHECK:-accept-new}" in yes|accept-new) ;; *) echo "DEPLOY_HOST_KEY_CHECK 는 yes 또는 accept-new" >&2; exit 1 ;; esac
ssh_opts=(-o "StrictHostKeyChecking=${DEPLOY_HOST_KEY_CHECK:-accept-new}")
if [ -n "${DEPLOY_KEY:-}" ]; then ssh_opts+=(-i "$DEPLOY_KEY" -o IdentitiesOnly=yes); fi
show_logs=1
if [ "${DEPLOY_SHOW_LOGS:-1}" = 0 ]; then show_logs=0; fi

rsync -az --delete --exclude .env -e "ssh ${ssh_opts[*]}" "$stage/" "$host:talk/"

ssh "${ssh_opts[@]}" "$host" "DEPLOY_SHOW_LOGS=$show_logs bash -s" <<'REMOTE'
set -euo pipefail
cd ~/talk
docker network create edge >/dev/null 2>&1 || true
mkdir -p ~/caddy-sites
docker compose up -d --build --remove-orphans
# Caddyfile 은 파일 하나만 마운트해서, rsync 로 바뀐 파일을 실행 중인 Caddy 가 보지 못한다. 다시 만들어 읽힌다
docker compose up -d --force-recreate --no-deps caddy
for _ in $(seq 90); do
  if curl -fs http://127.0.0.1:8080/health; then echo; echo "배포 완료"; exit 0; fi
  sleep 2
done
if [ "$DEPLOY_SHOW_LOGS" = 1 ]; then
  docker compose logs --tail 100 server
else
  docker compose ps
  echo "서버 로그는 공개 로그에 싣지 않는다 — 서버에서 docker compose logs server 로 본다" >&2
fi
echo "서버가 /health 에 답하지 않는다" >&2
exit 1
REMOTE
