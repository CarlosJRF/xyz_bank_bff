#!/usr/bin/env bash
# Prepares an Amazon Linux 2023 EC2 instance and starts the three systems of the Banco XYZ solution:
#
#   kafka_S8        Spring Cloud platform + Kafka   -> API Gateway on :80
#   xyz_bank_bff    BFF + domain microservices      -> HTTPS on :8443
#   xyz_bank_batch  Spring Batch + PostgreSQL       -> masterJob runs once after start
#
# Use it as EC2 "User data" (output goes to /var/log/cloud-init-output.log) or run it once over SSH:
#   sudo bash deploy/ec2-setup.sh
# Running it again updates the code, rebuilds the images and repeats the smoke tests.
#
# Variables (all optional):
#   APP_ROOT       where the repositories are cloned          (default: /opt/xyz-bank)
#   STACKS         stacks to start, space separated           (default: "kafka bff batch")
#   BRANCH         branch to deploy in every repository       (default: main)
#   GITHUB_USER    owner of the three repositories            (default: CarlosJRF)
#   GATEWAY_PORT   public port of the kafka_S8 gateway        (default: 80)
#   RUN_BATCH      run masterJob after PostgreSQL is healthy  (default: true)
#   SWAP_MB        swap file size in MB, 0 to skip            (default: 2048)
#   BUILDX_VERSION buildx plugin to install                   (default: v0.37.1; compose build needs >= 0.17.0)
set -euo pipefail
# cloud-init runs User data with a minimal environment; git and docker expect HOME.
export HOME="${HOME:-/root}"

APP_ROOT="${APP_ROOT:-/opt/xyz-bank}"
STACKS="${STACKS:-kafka bff batch}"
BRANCH="${BRANCH:-main}"
GITHUB_USER="${GITHUB_USER:-CarlosJRF}"
GATEWAY_PORT="${GATEWAY_PORT:-80}"
RUN_BATCH="${RUN_BATCH:-true}"
SWAP_MB="${SWAP_MB:-2048}"
BUILDX_VERSION="${BUILDX_VERSION:-v0.37.1}"

log() { printf '\n==> %s\n' "$*"; }
want() { [[ " $STACKS " == *" $1 "* ]]; }

# --- Docker Engine + Compose v2 and buildx plugins (same preparation as kafka_S8/deploy/ec2-setup.sh)
log "Preparing the host"
if ! command -v docker > /dev/null 2>&1 || ! command -v git > /dev/null 2>&1; then
  dnf install -y docker git
fi
if [ -d /run/systemd/system ]; then
  systemctl enable --now docker
fi
usermod -aG docker ec2-user 2> /dev/null || true

ARCH="$(uname -m)"   # x86_64 or aarch64 (Graviton)
PLUGINS=/usr/local/lib/docker/cli-plugins
mkdir -p "$PLUGINS"
if ! docker compose version > /dev/null 2>&1; then
  curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ARCH}" \
    -o "$PLUGINS/docker-compose"
  chmod +x "$PLUGINS/docker-compose"
fi

# The docker package of Amazon Linux ships without buildx (or with an old one), and
# "docker compose build" refuses to run with buildx older than 0.17.0.
BUILDX_MIN=0.17.0
current_buildx="$(docker buildx version 2> /dev/null | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | head -n1 | tr -d v || true)"
if [ -z "$current_buildx" ] \
    || [ "$(printf '%s\n%s\n' "$BUILDX_MIN" "$current_buildx" | sort -V | head -n1)" != "$BUILDX_MIN" ]; then
  case "$ARCH" in
    x86_64) BUILDX_ARCH=amd64 ;;
    aarch64) BUILDX_ARCH=arm64 ;;
    *) echo "Unsupported architecture: $ARCH" >&2; exit 1 ;;
  esac
  curl -fsSL "https://github.com/docker/buildx/releases/download/${BUILDX_VERSION}/buildx-${BUILDX_VERSION}.linux-${BUILDX_ARCH}" \
    -o "$PLUGINS/docker-buildx"
  chmod +x "$PLUGINS/docker-buildx"
fi
docker buildx version
docker compose version

# --- Swap: a safety net while the images are built (several Maven builds run at once)
if [ "$SWAP_MB" -gt 0 ] && ! swapon --show | grep -q /swapfile; then
  dd if=/dev/zero of=/swapfile bs=1M count="$SWAP_MB"
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap defaults 0 0' >> /etc/fstab
fi

# --- Helpers
sync_repo() {
  local dir="$APP_ROOT/$1"
  if [ -d "$dir/.git" ]; then
    git -C "$dir" fetch --depth 1 origin "$BRANCH"
    git -C "$dir" checkout -B "$BRANCH" "origin/$BRANCH"
  else
    git clone --depth 1 --branch "$BRANCH" "https://github.com/$GITHUB_USER/$1" "$dir"
  fi
}

# Creates .env from .env.example on the first run and sets KEY=VALUE in it.
set_env() {
  [ -f .env ] || cp .env.example .env
  if grep -q "^$1=" .env; then
    sed -i "s|^$1=.*|$1=$2|" .env
  else
    echo "$1=$2" >> .env
  fi
}

env_value() { grep -E "^$1=" "$2/.env" 2> /dev/null | tail -n1 | cut -d= -f2- || true; }

bff_port() {
  local port
  port="$(env_value BFF_PORT "$APP_ROOT/xyz_bank_bff")"
  echo "${port:-8443}"
}

mkdir -p "$APP_ROOT"

# --- Start the stacks, one after the other: images are built (multistage, inside Docker) on this host
if want kafka; then
  log "kafka_S8: platform (Config, Eureka, OAuth2, Gateway, Kafka, PostgreSQL)"
  sync_repo kafka_S8
  cd "$APP_ROOT/kafka_S8"
  set_env GATEWAY_PORT "$GATEWAY_PORT"
  docker compose up -d --build --wait
  docker compose ps
fi

if want bff; then
  log "xyz_bank_bff: BFF and domain microservices"
  sync_repo xyz_bank_bff
  cd "$APP_ROOT/xyz_bank_bff"
  [ -f .env ] || cp .env.example .env
  docker compose up -d --build --wait
  docker compose ps
fi

if want batch; then
  log "xyz_bank_batch: PostgreSQL and Spring Batch"
  sync_repo xyz_bank_batch
  cd "$APP_ROOT/xyz_bank_batch"
  [ -f .env ] || cp .env.example .env
  docker compose up -d --wait
  if [ "$RUN_BATCH" = "true" ]; then
    docker compose run --rm --build batch
  fi
fi

# --- Smoke tests: each one exercises a real path through the deployed system
gateway_ok() {
  local dir="$APP_ROOT/kafka_S8" id secret body token
  id="$(env_value BANKING_CLIENT_ID "$dir")"; secret="$(env_value BANKING_CLIENT_SECRET "$dir")"
  body="$(curl -fsS -u "${id:-banking-client}:${secret:-banking-secret}" \
    -d grant_type=client_credentials -d "scope=data.read ingestion.write" \
    "http://localhost:$GATEWAY_PORT/oauth2/token")" || return 1
  token="$(printf '%s' "$body" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
  [ -n "$token" ] || return 1
  curl -fsS -H "Authorization: Bearer $token" "http://localhost:$GATEWAY_PORT/api/stats" > /dev/null
}

bff_ok() {
  local port
  port="$(bff_port)"
  curl -fsSk -u clienteWeb:web123 "https://localhost:$port/api/web/cuentas/101" > /dev/null \
    && curl -fsSk -u clienteMovil:movil123 "https://localhost:$port/api/movil/cuentas/101" > /dev/null \
    && curl -fsSk -u atmXyz:atm123 "https://localhost:$port/api/cajero/cuentas/101/saldo" > /dev/null
}

batch_ok() {
  local dir="$APP_ROOT/xyz_bank_batch" user rows
  user="$(env_value DB_USER "$dir")"
  rows="$(cd "$dir" && docker compose exec -T postgres psql -U "${user:-xyzbank}" -d xyzbank -tAc \
    'SELECT count(*) FROM transaction_report')" || return 1
  [ "${rows:-0}" -gt 0 ]
}

log "Smoke tests"
FAILS=0
run_check() {
  local label="$1"; shift
  if "$@"; then
    printf '  OK     %s\n' "$label"
  else
    printf '  FALLO  %s\n' "$label"
    FAILS=$((FAILS + 1))
  fi
}
want kafka && run_check "kafka_S8: token OAuth2 y GET /api/stats por el gateway (:$GATEWAY_PORT)" gateway_ok
want bff && run_check "xyz_bank_bff: canales web, móvil y cajero con datos de cuenta" bff_ok
if want batch && [ "$RUN_BATCH" = "true" ]; then
  run_check "xyz_bank_batch: transacciones persistidas en PostgreSQL" batch_ok
fi

# --- Summary
IMDS_TOKEN="$(curl -fsS --max-time 2 -X PUT http://169.254.169.254/latest/api/token \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2> /dev/null || true)"
PUBLIC_IP="$(curl -fsS --max-time 2 -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" \
  http://169.254.169.254/latest/meta-data/public-ipv4 2> /dev/null || true)"
HOST="${PUBLIC_IP:-<IP-publica>}"

log "Ready"
want kafka && echo "  Gateway kafka_S8: http://$HOST:$GATEWAY_PORT/oauth2/token  y  /api/**"
want bff && echo "  BFF:              https://$HOST:$(bff_port)/api/{web,movil,cajero}/cuentas/..."
want kafka && echo "  Paneles (solo 127.0.0.1): ssh -L 8761:localhost:8761 -L 8083:localhost:8083 ec2-user@$HOST"
echo "  Repositorios en $APP_ROOT"

if [ "$FAILS" -gt 0 ]; then
  echo "  $FAILS prueba(s) fallaron: revisa 'docker compose logs' en el stack correspondiente." >&2
  exit 1
fi
