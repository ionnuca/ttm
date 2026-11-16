#!/usr/bin/env bash
# Pornirea și oprirea aplicației de test din aplicația de producție (Setări → Aplicația de test).
#
# Aplicația de producție NU are acces la Docker: scrie doar starea dorită („on”/„off”) în
# /opt/ttm/control/test-desired. Serviciul systemd instalat aici (ca root) urmărește fișierul,
# pornește sau oprește /opt/ttm-test și scrie starea reală în /opt/ttm/control/test-status.
#
# Se rulează o singură dată pe server, ca root:
#   curl -fsSL https://raw.githubusercontent.com/ionnuca/ttm/main/deploy/test-control-setup.sh -o test-control-setup.sh
#   sudo bash test-control-setup.sh
# Implicit, aplicația de test rămâne OPRITĂ; se pornește din Setări când e nevoie.
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Rulați cu sudo: sudo bash test-control-setup.sh" >&2
  exit 1
fi

PROD=/opt/ttm
CONTROL=$PROD/control
step() { echo; echo "==> $*"; }

step "Folderul comun $CONTROL"
mkdir -p "$CONTROL"
# utilizatorul aplicației din container (ttm) trebuie să poată scrie starea dorită
APP_UID=$(cd "$PROD" && docker compose exec -T app id -u 2>/dev/null | tr -d '\r' || true)
if [[ "$APP_UID" =~ ^[0-9]+$ ]]; then
  chown "$APP_UID" "$CONTROL"
  chmod 755 "$CONTROL"
  echo "Proprietar: uid $APP_UID (utilizatorul aplicației)"
else
  chmod 777 "$CONTROL"
  echo "Aplicația nu rulează acum: folderul rămâne deschis la scriere (chmod 777)."
fi
if [[ ! -f "$CONTROL/test-desired" ]]; then
  echo off > "$CONTROL/test-desired"
  [[ "$APP_UID" =~ ^[0-9]+$ ]] && chown "$APP_UID" "$CONTROL/test-desired"
  echo "Starea implicită: oprită"
fi

step "Scriptul de comandă /usr/local/sbin/ttm-test-control"
cat > /usr/local/sbin/ttm-test-control <<'SCRIPT'
#!/usr/bin/env bash
# Aplică starea dorită a aplicației de test și scrie starea reală (rulat de systemd).
set -u
CONTROL=/opt/ttm/control
TEST=/opt/ttm-test
desired=$(tr -d '[:space:]' < "$CONTROL/test-desired" 2>/dev/null || true)
[[ "$desired" == on ]] || desired=off

container() { docker ps -aq --filter label=com.docker.compose.project=ttm-test --filter label=com.docker.compose.service=app-test | head -1; }
running() { local c; c=$(container); [[ -n "$c" ]] && [[ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" == true ]]; }

if [[ -f "$TEST/docker-compose.yml" ]]; then
  if [[ "$desired" == on ]] && ! running; then
    (cd "$TEST" && docker compose up -d >/dev/null 2>&1) || true
  elif [[ "$desired" == off ]] && running; then
    (cd "$TEST" && docker compose stop >/dev/null 2>&1) || true
  fi
fi

state=stopped
if running; then
  c=$(container)
  since=$(docker inspect -f '{{.State.StartedAt}}' "$c")
  if docker logs --since "$since" "$c" 2>&1 | grep -q 'Started TtmApplication'; then state=running; else state=starting; fi
fi
printf '%s %s\n' "$state" "$(date -Iseconds)" > "$CONTROL/test-status.tmp"
chmod 644 "$CONTROL/test-status.tmp"
mv -f "$CONTROL/test-status.tmp" "$CONTROL/test-status"
SCRIPT
chmod 755 /usr/local/sbin/ttm-test-control

step "Serviciul systemd"
cat > /etc/systemd/system/ttm-test-control.service <<'UNIT'
[Unit]
Description=TTM: pornește/oprește aplicația de test după starea cerută din aplicație
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/ttm-test-control
UNIT

cat > /etc/systemd/system/ttm-test-control.path <<'UNIT'
[Unit]
Description=TTM: urmărește starea cerută a aplicației de test

[Path]
PathChanged=/opt/ttm/control/test-desired
Unit=ttm-test-control.service

[Install]
WantedBy=multi-user.target
UNIT

cat > /etc/systemd/system/ttm-test-control.timer <<'UNIT'
[Unit]
Description=TTM: verifică periodic starea aplicației de test

[Timer]
OnBootSec=1min
OnUnitActiveSec=20s
AccuracySec=5s
Unit=ttm-test-control.service

[Install]
WantedBy=timers.target
UNIT

systemctl daemon-reload
systemctl enable --now ttm-test-control.path ttm-test-control.timer
systemctl start ttm-test-control.service

step "Starea de acum"
cat "$CONTROL/test-status"
echo
echo "Gata. Aplicația de test se pornește și se oprește din Setări → Aplicația de test."
echo "Repornirea aplicației de producție (pentru folderul comun) se face la următoarea publicare,"
echo "sau acum: cd $PROD && docker compose up -d"
