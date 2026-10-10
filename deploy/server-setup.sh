#!/usr/bin/env bash
# =====================================================================
# Pregătirea unui VPS nou (Ubuntu 22.04 / 24.04) pentru TTM. Se rulează o singură dată:
#
#   curl -fsSLO https://raw.githubusercontent.com/ionnuca/ttm/main/deploy/server-setup.sh
#   sudo bash server-setup.sh      (ca root: bash server-setup.sh)
#
# Ce face:
#   - actualizări de securitate automate, swap de 2 GB, firewall (SSH, HTTP, HTTPS)
#   - instalează Docker
#   - creează utilizatorul "deploy", folosit de GitHub Actions pentru publicare, cu o cheie SSH dedicată
#   - pregătește /opt/ttm: docker-compose.yml, Caddyfile, backup.sh și .env (întrebări interactive)
#   - backup zilnic al bazei de date la 03:30
# Scriptul poate fi rulat din nou fără probleme: pașii deja făcuți sunt săriți.
# =====================================================================
set -euo pipefail

REPO_RAW="https://raw.githubusercontent.com/ionnuca/ttm/main/deploy"
APP_DIR="/opt/ttm"
DEPLOY_USER="deploy"

if [[ $EUID -ne 0 ]]; then
  echo "Rulați ca root (bash server-setup.sh) sau cu sudo (sudo bash server-setup.sh)" >&2
  exit 1
fi

step() { echo; echo "==> $*"; }

step "Actualizarea sistemului"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get upgrade -yq
apt-get install -yq ca-certificates curl ufw unattended-upgrades cron openssl
dpkg-reconfigure -f noninteractive unattended-upgrades

step "Fusul orar: Europe/Chisinau"
timedatectl set-timezone Europe/Chisinau || true

step "Swap de 2 GB (rezervă de memorie)"
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
else
  echo "Există deja swap"
fi

step "Docker"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

step "Firewall: SSH, HTTP, HTTPS"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

step "Utilizatorul '$DEPLOY_USER' pentru publicarea din GitHub Actions"
if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "$DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"
DEPLOY_HOME="/home/$DEPLOY_USER"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DEPLOY_HOME/.ssh"
KEY="$DEPLOY_HOME/.ssh/github_actions"
NEW_KEY=false
if [[ ! -f "$KEY" ]]; then
  sudo -u "$DEPLOY_USER" ssh-keygen -t ed25519 -N "" -C "github-actions-ttm" -f "$KEY" -q
  NEW_KEY=true
fi
touch "$DEPLOY_HOME/.ssh/authorized_keys"
grep -qF "$(cat "$KEY.pub")" "$DEPLOY_HOME/.ssh/authorized_keys" || cat "$KEY.pub" >> "$DEPLOY_HOME/.ssh/authorized_keys"
chown "$DEPLOY_USER:$DEPLOY_USER" "$DEPLOY_HOME/.ssh/authorized_keys"
chmod 600 "$DEPLOY_HOME/.ssh/authorized_keys"

step "Fișierele aplicației în $APP_DIR"
install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR" "$APP_DIR/backups"
curl -fsSL "$REPO_RAW/docker-compose.prod.yml" -o "$APP_DIR/docker-compose.yml"
curl -fsSL "$REPO_RAW/Caddyfile" -o "$APP_DIR/Caddyfile"
curl -fsSL "$REPO_RAW/backup.sh" -o "$APP_DIR/backup.sh"
chmod +x "$APP_DIR/backup.sh"

if [[ ! -f "$APP_DIR/.env" ]]; then
  step "Configurarea aplicației (.env)"
  read -rp "Domeniul aplicației (ex. turnee.exemplu.md): " APP_DOMAIN
  read -rp "Email pentru certificatul HTTPS (Let's Encrypt): " ACME_EMAIL
  read -rp "Utilizatorul administratorului [ionnuca]: " ADMIN_USER
  read -rp "Prenumele administratorului [Ion]: " ADMIN_FIRST
  read -rp "Numele administratorului [Nuca]: " ADMIN_LAST
  while true; do
    read -rsp "Parola administratorului (minimum 8 caractere): " ADMIN_PASS; echo
    read -rsp "Repetați parola: " ADMIN_PASS2; echo
    [[ ${#ADMIN_PASS} -ge 8 && "$ADMIN_PASS" == "$ADMIN_PASS2" && "$ADMIN_PASS" != *"'"* ]] && break
    echo "Parolele nu coincid, sunt prea scurte sau conțin apostrof ('). Încercați din nou."
  done
  read -rp "Adaug 10 jucători de test la prima pornire? [D/n]: " SEED
  [[ "${SEED,,}" == "n" ]] && PROFILE="" || PROFILE="seed"

  # Memoria pentru Java după memoria serverului (PostgreSQL, Caddy și sistemul au nevoie de restul)
  MEM_MB=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
  if (( MEM_MB < 3000 )); then HEAP=640m; else HEAP=1g; fi
  echo "Memorie: ${MEM_MB} MB -> Java -Xmx${HEAP}"

  umask 077
  cat > "$APP_DIR/.env" <<ENV
# Generat de server-setup.sh. Nu urcați acest fișier în Git.
APP_DOMAIN=$APP_DOMAIN
ACME_EMAIL=$ACME_EMAIL
DB_PASSWORD=$(openssl rand -hex 24)
APP_ADMIN_USERNAME=${ADMIN_USER:-ionnuca}
APP_ADMIN_PASSWORD='$ADMIN_PASS'
APP_ADMIN_FIRST_NAME=${ADMIN_FIRST:-Ion}
APP_ADMIN_LAST_NAME=${ADMIN_LAST:-Nuca}
APP_REGISTRATION_ENABLED=true
# "seed" adaugă 10 jucători de test doar într-o bază goală; poate rămâne setat
SPRING_PROFILES_ACTIVE=$PROFILE
JAVA_OPTS=-Xmx$HEAP -XX:+UseSerialGC
TTM_IMAGE=ghcr.io/ionnuca/ttm:latest
ENV
  chown "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR/.env"
  chmod 600 "$APP_DIR/.env"
else
  echo "$APP_DIR/.env există deja, nu îl modific"
fi
chown -R "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR"

step "Backup zilnic al bazei de date (03:30, păstrat 14 zile)"
cat > /etc/cron.d/ttm-backup <<CRON
30 3 * * * $DEPLOY_USER $APP_DIR/backup.sh >> $APP_DIR/backups/backup.log 2>&1
CRON
chmod 644 /etc/cron.d/ttm-backup

IP=$(curl -fsS4 https://ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
echo
echo "=============================================================="
echo " Serverul e pregătit."
echo
echo " 1) DNS: înregistrare de tip A pentru domeniu -> $IP"
echo
echo " 2) GitHub: ionnuca/ttm -> Settings -> Secrets and variables -> Actions"
echo "    -> New repository secret, câte unul pentru fiecare:"
echo "      DEPLOY_HOST     = $IP"
echo "      DEPLOY_USER     = $DEPLOY_USER"
echo "      DEPLOY_SSH_KEY  = tot textul cheii de mai jos, inclusiv liniile BEGIN/END"
echo
if $NEW_KEY; then
  echo "----- cheia privată pentru DEPLOY_SSH_KEY (copiați-o acum) -----"
  cat "$KEY"
  echo "----------------------------------------------------------------"
else
  echo "    Cheia privată există deja: sudo cat $KEY"
fi
echo
echo " 3) GitHub: Actions -> Publicare -> Run workflow."
echo "    Prima pornire durează 1-2 minute; apoi aplicația e la https://<domeniu>"
echo "=============================================================="
