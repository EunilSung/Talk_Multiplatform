#!/usr/bin/env bash
# 서버를 빌드해 인스턴스에 올리고 다시 띄운다. 배포하는 PC 에서 돌린다.
#   DEPLOY_HOST=ubuntu@<공인IP> deploy/deploy.sh
# 서버의 ~/talk/.env 는 건드리지 않는다. 처음 한 번은 setup-host.sh 와 .env 준비가 먼저다.
set -euo pipefail

host="${DEPLOY_HOST:?DEPLOY_HOST=ubuntu@<공인IP> 를 준다}"
key="${DEPLOY_KEY:-$HOME/.ssh/oracle_test_server}"
root="$(cd "$(dirname "$0")/.." && pwd)"
stage="$root/build/deploy"
ssh_cmd="ssh -i $key -o StrictHostKeyChecking=accept-new"

cd "$root"
SERVER_ONLY=1 ./gradlew :server:installDist --quiet

rm -rf "$stage"
mkdir -p "$stage"
cp deploy/Dockerfile deploy/docker-compose.yml deploy/Caddyfile deploy/.env.example \
   deploy/setup-host.sh deploy/backup.sh "$stage/"
cp -R server/build/install/server "$stage/server"

rsync -az --delete --exclude .env -e "$ssh_cmd" "$stage/" "$host:talk/"

$ssh_cmd "$host" 'bash -s' <<'REMOTE'
set -euo pipefail
cd ~/talk
docker network create edge >/dev/null 2>&1 || true
mkdir -p ~/caddy-sites
docker compose up -d --build --remove-orphans
for _ in $(seq 90); do
  if curl -fs http://127.0.0.1:8080/health; then echo; echo "배포 완료"; exit 0; fi
  sleep 2
done
docker compose logs --tail 100 server
echo "서버가 /health 에 답하지 않는다" >&2
exit 1
REMOTE
