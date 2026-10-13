#!/usr/bin/env bash
# Copiază datele din producție (baza ttm) în baza de test (ttm_test) și repornește aplicația de test.
# Datele de test de până acum se pierd. Rulare pe server:  /opt/ttm-test/test-refresh.sh
set -euo pipefail
PROD=/opt/ttm
TEST=/opt/ttm-test

cd "$TEST"
docker compose stop app-test > /dev/null 2>&1 || true

cd "$PROD"
docker compose exec -T db psql -q -U ttm -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS ttm_test WITH (FORCE)" \
  -c "CREATE DATABASE ttm_test OWNER ttm"
docker compose exec -T db sh -c "pg_dump -U ttm -d ttm --no-owner | psql -q -U ttm -d ttm_test -v ON_ERROR_STOP=1" > /dev/null
echo "Datele din producție au fost copiate în baza de test ($(date '+%Y-%m-%d %H:%M'))"

cd "$TEST"
if docker image inspect ghcr.io/ionnuca/ttm:develop > /dev/null 2>&1; then
  docker compose up -d app-test
  echo "Aplicația de test repornește (aplică migrările din develop pe copie)"
else
  echo "Imaginea de test nu e încă pe server: va fi instalată la următorul commit pe develop"
fi
