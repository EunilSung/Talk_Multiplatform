#!/usr/bin/env bash
# PostgreSQL 을 하루 한 번 덤프해 ~/backups 에 7개까지 남긴다. setup-host.sh 가 cron 에 건다.
set -euo pipefail
dir="$HOME/backups"
mkdir -p "$dir"
docker compose -f "$HOME/talk/docker-compose.yml" exec -T db pg_dump -U talk -d talk | gzip > "$dir/talk-$(date +%F).sql.gz"
ls -1t "$dir"/talk-*.sql.gz | tail -n +8 | xargs -r rm --
