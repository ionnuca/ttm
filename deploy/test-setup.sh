#!/usr/bin/env bash
# =====================================================================
# Pregătește aplicația de test pe serverul de producție. Se rulează o singură dată, ca root:
#
#   curl -fsSLO https://raw.githubusercontent.com/ionnuca/ttm/main/deploy/test-setup.sh
#   bash test-setup.sh
#
# Ce face:
#   - /opt/ttm-test: docker-compose.yml, .env (parola bazei, preluată din producție), test-refresh.sh
#   - copiază datele din producție în baza ttm_test
#   - adresa de test în Caddy (/opt/ttm/sites/test.caddy); accesul e ca în producție, cu conturile aplicației
# Aplicația de test se instalează și se actualizează apoi automat, după fiecare commit pe develop.
# Poate fi rulat din nou (de exemplu pentru altă adresă).
# =====================================================================
set -euo pipefail

REPO_RAW="https://raw.githubusercontent.com/ionnuca/ttm/main/deploy"
PROD=/opt/ttm
TEST=/opt/ttm-test
DEPLOY_USER=deploy

if [[ $EUID -ne 0 ]]; then
  echo "Rulați ca root (bash test-setup.sh)" >&2
  exit 1
fi
if [[ ! -f "$PROD/.env" ]]; then
  echo "Producția nu e instalată în $PROD (rulați întâi server-setup.sh)" >&2
  exit 1
fi
if ! grep -q "import /etc/caddy/sites" "$PROD/Caddyfile" || ! grep -q "./sites:/etc/caddy/sites" "$PROD/docker-compose.yml"; then
  echo "Producția are o versiune veche a configurației: publicați întâi versiunea 1.9.3 sau mai nouă" >&2
  exit 1
fi

step() { echo; echo "==> $*"; }

step "Fișierele aplicației de test în $TEST"
install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$TEST" "$PROD/sites"
curl -fsSL "$REPO_RAW/docker-compose.test.yml" -o "$TEST/docker-compose.yml"
curl -fsSL "$REPO_RAW/test-refresh.sh" -o "$TEST/test-refresh.sh"
chmod +x "$TEST/test-refresh.sh"
umask 077
grep '^DB_PASSWORD=' "$PROD/.env" > "$TEST/.env"
umask 022
chown -R "$DEPLOY_USER:$DEPLOY_USER" "$TEST"
chmod 600 "$TEST/.env"

step "Adresa aplicației de test"
read -rp "Adresa de test [test.tabletennis.omagiu.md]: " DOMAIN
DOMAIN=${DOMAIN:-test.tabletennis.omagiu.md}
cd "$PROD"
cat > "$PROD/sites/test.caddy" <<CADDY
# Aplicația de test (versiunea din develop), creată de test-setup.sh
$DOMAIN {
	encode zstd gzip
	reverse_proxy app-test:8080
}
CADDY
chown -R "$DEPLOY_USER:$DEPLOY_USER" "$PROD/sites"

step "Copia datelor din producție"
sudo -u "$DEPLOY_USER" "$TEST/test-refresh.sh"

step "Caddy: adresa de test"
docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile

echo
echo "=============================================================="
echo " Gata. Adresa de test: https://$DOMAIN"
echo " Aplicația de test se instalează automat la următorul commit pe develop."
echo " Datele din producție, copiate din nou oricând:  $TEST/test-refresh.sh"
echo "=============================================================="
