#!/usr/bin/env bash
# Backup zilnic al bazei de date (rulat de cron la 03:30, vezi server-setup.sh).
# Păstrează copiile din ultimele 14 zile în /opt/ttm/backups.
# Restaurare:  docker compose exec -T db pg_restore -U ttm -d ttm --clean --if-exists < backups/<fișier>.dump
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p backups
file="backups/ttm-$(date +%F-%H%M).dump"
docker compose exec -T db pg_dump -U ttm -Fc ttm > "$file"
find backups -name 'ttm-*.dump' -mtime +14 -delete
echo "$(date -Is) backup: $file ($(du -h "$file" | cut -f1))"
