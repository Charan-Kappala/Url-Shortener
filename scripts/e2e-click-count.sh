#!/usr/bin/env bash
# End-to-end check of the click pipeline on the full compose stack:
# N redirects must produce exactly N counted clicks, even though the analytics consumer is
# killed with SIGKILL (no graceful shutdown) and restarted while events are flowing.
#
# Usage: scripts/e2e-click-count.sh   (env N, default 3000)
set -euo pipefail
cd "$(dirname "$0")/.."
N=${N:-3000}
STATUS_FILE=$(mktemp)
export RATE_LIMIT_REDIRECT_PER_MINUTE=1000000000   # all requests come from this one machine

psql_q() { docker compose exec -T postgres psql -U postgres -d urlshortener -At -c "$1"; }

docker compose down -v --remove-orphans >/dev/null 2>&1
docker compose up -d --wait postgres redis localstack >/dev/null
docker compose up -d core-api analytics-consumer >/dev/null
for _ in $(seq 1 120); do curl -sf localhost:8080/actuator/health >/dev/null && break; sleep 1; done

code=$(curl -sf -X POST localhost:8080/api/v1/urls -H 'Content-Type: application/json' \
  -d '{"originalUrl":"https://example.com/e2e"}' | jq -r .shortCode)
echo "Created $code; sending $N redirects (16 in parallel)"

seq "$N" | xargs -P 16 -I{} curl -s -o /dev/null -w '%{http_code}\n' "localhost:8080/$code" > "$STATUS_FILE" &
redirects=$!

sleep 4
echo "Killing analytics-consumer (SIGKILL) mid-stream"
docker compose kill -s SIGKILL analytics-consumer >/dev/null
sleep 5
docker compose up -d analytics-consumer >/dev/null
echo "Consumer restarted"

wait $redirects
ok=$(grep -c '^302$' "$STATUS_FILE" || true); rm -f "$STATUS_FILE"
echo "$ok of $N redirects returned 302"

# Messages the killed consumer had received but not deleted reappear after the 30 s visibility timeout.
for _ in $(seq 1 90); do
  counted=$(psql_q "SELECT click_count FROM url_mappings WHERE short_code = '$code'")
  [ "$counted" -ge "$ok" ] && break
  sleep 2
done
sleep 5   # anything late (a double count) would show up now
counted=$(psql_q "SELECT click_count FROM url_mappings WHERE short_code = '$code'")
stored=$(psql_q "SELECT count(*) FROM click_events WHERE short_code = '$code'")
dropped=$(curl -sf "localhost:8080/actuator/metrics/clicks.dropped" | jq '.measurements[0].value')

echo "click_count=$counted  click_events rows=$stored  dropped by publisher=$dropped"
if [ "$counted" -eq "$ok" ] && [ "$stored" -eq "$ok" ]; then
  echo "PASS: $ok redirects -> exactly $counted counted clicks"
else
  echo "FAIL: expected $ok"; exit 1
fi
