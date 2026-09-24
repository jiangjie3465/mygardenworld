#!/usr/bin/env bash
# Build gardend with the embedded Web UI and deploy it to the production host.
#
#   deploy/deploy.sh               build, upload, back up, install, restart
#   deploy/deploy.sh --skip-build  reuse bin/gardend-linux-amd64
#
# DEPLOY_HOST (default garden-prod) is an ssh destination; configure its key
# in ~/.ssh/config. The remote side stops gardend, copies the database and the
# current binary into /opt/mygardenworld/backups/<timestamp>, installs the new
# binary and restarts. If the service does not stay active, both are restored.
set -euo pipefail

HOST="${DEPLOY_HOST:-garden-prod}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/bin/gardend-linux-amd64"
SKIP_BUILD=0

for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

log() { printf '\033[1;32m==>\033[0m %s\n' "$*"; }

cd "$ROOT"
REV="$(git rev-parse --short HEAD)$(git diff --quiet HEAD || echo '-dirty')"

if [[ $SKIP_BUILD -eq 0 ]]; then
  PNPM=(pnpm)
  if command -v corepack >/dev/null; then PNPM=(corepack pnpm@10); fi

  log "building web ($REV)"
  "${PNPM[@]}" --dir web install --frozen-lockfile >/dev/null
  NEXT_PUBLIC_API_URL= "${PNPM[@]}" --dir web build >/dev/null
  find internal/webui/static -mindepth 1 ! -name placeholder.txt -exec rm -rf {} +
  cp -R web/out/. internal/webui/static/

  log "building gardend linux/amd64"
  mkdir -p bin
  CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -o "$OUT" ./cmd/gardend
fi
[[ -f "$OUT" ]] || { echo "missing $OUT; run without --skip-build" >&2; exit 1; }

SUM="$(shasum -a 256 "$OUT" | cut -d' ' -f1)"
log "uploading to $HOST"
scp -q "$OUT" "$HOST:/tmp/gardend.new"

log "installing on $HOST"
ssh "$HOST" "EXPECTED_SUM=$SUM REV=$REV bash -s" <<'REMOTE'
set -euo pipefail
APP=/opt/mygardenworld
DB=$APP/data/garden.db
BACKUP=$APP/backups/$(date +%Y%m%d-%H%M%S)-$REV

echo "$EXPECTED_SUM  /tmp/gardend.new" | sha256sum -c --quiet

systemctl stop gardend
mkdir -p "$BACKUP"
cp -a "$APP/bin/gardend" "$BACKUP/gardend"
cp -a "$APP"/data/garden.db* "$BACKUP/"
echo "backup: $BACKUP"

# One-time repair for databases created by the Android branch before it
# merged upstream: its mobile-token migration was numbered v7, colliding with
# upstream's v7. Rewind so upstream v7..v16 run; the mobile migration (now
# v17) skips columns that already exist. No-op for every other database.
if [[ "$(sqlite3 "$DB" 'PRAGMA user_version;')" == 7 ]] \
  && [[ -n "$(sqlite3 "$DB" "SELECT 1 FROM pragma_table_info('refresh_tokens') WHERE name='client_type';")" ]] \
  && [[ -z "$(sqlite3 "$DB" "SELECT 1 FROM pragma_table_info('redeem_codes') WHERE name='expiry_overridden';")" ]]; then
  echo "rewinding schema v7 (android mobile tokens) to v6 before upstream migrations"
  sqlite3 "$DB" 'PRAGMA user_version = 6;'
fi

install -o root -g root -m 755 /tmp/gardend.new "$APP/bin/gardend"
rm -f /tmp/gardend.new
systemctl start gardend

sleep 8
if ! systemctl is-active --quiet gardend; then
  echo "gardend failed to stay active; restoring $BACKUP" >&2
  journalctl -u gardend -n 60 --no-pager >&2 || true
  systemctl stop gardend || true
  install -o root -g root -m 755 "$BACKUP/gardend" "$APP/bin/gardend"
  rm -f "$APP"/data/garden.db-wal "$APP"/data/garden.db-shm
  cp -a "$BACKUP"/garden.db* "$APP/data/"
  systemctl start gardend
  exit 1
fi

echo "schema version: $(sqlite3 "$DB" 'PRAGMA user_version;')"
journalctl -u gardend -n 15 --no-pager
# Keep the ten most recent backups.
ls -1dt "$APP"/backups/*/ | tail -n +11 | xargs -r rm -rf
REMOTE

log "deployed $REV to $HOST"
