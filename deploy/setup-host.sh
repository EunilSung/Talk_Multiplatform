#!/usr/bin/env bash
# 새 인스턴스(Ubuntu 24.04)를 한 번 준비한다 — 스왑, Docker, 방화벽(80·443), DB 백업 cron.
# 서버에서 sudo 로 돌린다: sudo bash setup-host.sh
set -euo pipefail

if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/99-swap.conf
sysctl -p /etc/sysctl.d/99-swap.conf

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io docker-compose-v2 rsync curl iptables-persistent
systemctl enable --now docker
usermod -aG docker "${SUDO_USER:-ubuntu}"

# OCI 의 Ubuntu 이미지는 iptables 가 22 번 말고 모두 막는다. REJECT 앞에 80·443 을 연다.
for port in 80 443; do
  iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 5 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
netfilter-persistent save

install -m 755 "$(dirname "$0")/backup.sh" /usr/local/bin/talk-backup
echo "30 3 * * * ${SUDO_USER:-ubuntu} /usr/local/bin/talk-backup" > /etc/cron.d/talk-backup
