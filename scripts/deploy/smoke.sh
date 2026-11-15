#!/usr/bin/env bash
set -Eeuo pipefail

mode="${1:-full}"
if [[ "$mode" != basic && "$mode" != full ]]; then
  echo 'Usage: smoke.sh basic|full' >&2
  exit 2
fi
script_dir="$(cd "$(dirname "$0")" && pwd)"
if [[ -f "$script_dir/compose.yaml" ]]; then
  cd "$script_dir"
else
  cd "$script_dir/../.."
fi

compose_args=(docker compose)
if [[ -f .env ]]; then
  compose_args+=(--env-file .env)
elif [[ "$mode" == full ]]; then
  echo 'FAIL: Staging .env is missing' >&2
  exit 1
fi
compose_args+=(-f compose.yaml)
if [[ -n "${BACKEND_IMAGE:-}" && -n "${FRONTEND_IMAGE:-}" ]]; then
  compose_args+=(-f compose.staging.yaml)
fi
compose() { "${compose_args[@]}" "$@"; }

for service in mysql backend frontend; do
  container="$(compose ps -q "$service")"
  [[ -n "$container" ]] || { echo "FAIL: $service container is absent" >&2; exit 1; }
  health="$(docker inspect --format '{{.State.Health.Status}}' "$container")"
  [[ "$health" == healthy ]] || { echo "FAIL: $service health=$health" >&2; exit 1; }
  echo "PASS: $service healthy"
done

base="http://127.0.0.1:${FRONTEND_PORT:-8088}"
page="$(curl -fsS --retry 5 --retry-connrefused --retry-delay 2 --max-time 10 "$base/")"
grep -q '<div id="root"' <<< "$page" || { echo 'FAIL: frontend HTML is unexpected' >&2; exit 1; }
echo 'PASS: frontend HTTP'

curl -fsS --retry 5 --retry-connrefused --retry-delay 2 --max-time 10 "$base/api/public/languages" \
  | jq -e 'type == "array" and length > 0' >/dev/null
curl -fsS --max-time 10 "$base/api/public/pois" | jq -e 'type == "array"' >/dev/null
echo 'PASS: backend public API and MySQL-backed POIs'

mysql_scalar() {
  compose exec -T mysql sh -ec \
    'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=TCP -h 127.0.0.1 -u "$MYSQL_USER" "$MYSQL_DATABASE" -Nse "$1"' \
    sh "$1"
}
[[ "$(mysql_scalar 'SELECT 1')" == 1 ]] || { echo 'FAIL: MySQL query' >&2; exit 1; }
rank="$(mysql_scalar 'SELECT COALESCE(MAX(installed_rank),0) FROM flyway_schema_history')"
failed="$(mysql_scalar 'SELECT COUNT(*) FROM flyway_schema_history WHERE success <> 1')"
[[ "$rank" =~ ^[1-9][0-9]*$ && "$failed" == 0 ]] || { echo 'FAIL: Flyway history' >&2; exit 1; }
mysql_scalar 'SELECT COUNT(*) FROM pipeline_task' >/dev/null
echo "PASS: MySQL, Flyway rank $rank, pipeline table"

if [[ "$mode" == basic ]]; then
  echo 'Basic Compose smoke PASS; Admin Monitoring and external TTS are reserved for Staging.'
  exit 0
fi

[[ -f .env.smoke ]] || { echo 'FAIL: .env.smoke is missing' >&2; exit 1; }
# This is an operator-owned 0600 Bash environment file on the server, never in Git.
set -a
source .env.smoke
set +a
: "${STAGING_ADMIN_EMAIL:?Set STAGING_ADMIN_EMAIL in .env.smoke}"
: "${STAGING_ADMIN_PASSWORD:?Set STAGING_ADMIN_PASSWORD in .env.smoke}"
cookie="$(mktemp)"
trap 'rm -f "$cookie"' EXIT
payload="$(jq -nc --arg email "$STAGING_ADMIN_EMAIL" --arg password "$STAGING_ADMIN_PASSWORD" \
  '{email: $email, password: $password}')"
curl -fsS --max-time 10 -c "$cookie" -H 'Content-Type: application/json' \
  --data-binary "$payload" "$base/api/admin/auth/login" >/dev/null
curl -fsS --max-time 10 -b "$cookie" "$base/api/admin/monitoring/dashboard" \
  | jq -e '.databaseStatus == "NORMAL" and .dataUnavailable != true and
      (.pipelineStatus | type == "string") and (.activeUsers | type == "number")' >/dev/null
echo 'PASS: authenticated Monitoring Dashboard and pipeline counters'

provider="$(compose exec -T backend printenv TTS_PROVIDER | tr -d '\r')"
[[ "$provider" == edge ]] || { echo "FAIL: live TTS smoke supports Edge; configured $provider" >&2; exit 1; }
compose exec -T backend sh -ec '
  tmp="$(mktemp -d)"
  trap "rm -rf \"$tmp\"" EXIT
  printf "Kiểm tra giọng nói VoiceMap.\n" > "$tmp/input.txt"
  # The live Edge service can return no audio transiently; use bounded worker retries.
  /opt/edge-tts/bin/python /app/scripts/edge_tts_worker.py \
    --input "$tmp/input.txt" --output "$tmp/output.mp3" \
    --voice vi-VN-HoaiMyNeural --attempts 3 >/dev/null
  test -s "$tmp/output.mp3"
'
echo 'PASS: live Edge TTS generated audio in a temporary file'
echo 'Full Staging smoke PASS'
