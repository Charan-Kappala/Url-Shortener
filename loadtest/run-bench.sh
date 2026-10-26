#!/usr/bin/env bash
# Runs the redirect load test N times, each on a freshly created stack (empty Postgres volume, empty Redis).
#
# Usage: loadtest/run-bench.sh <label> <cache: on|off> [vus=100] [runs=3]
# Env:   HOLD (default 2m), LINKS (default 10000)
# Output: loadtest/results/<label>/cache-<on|off>-run<N>.json  (+ environment.json)
set -euo pipefail
cd "$(dirname "$0")/.."

LABEL=$1; CACHE=$2; VUS=${3:-100}; RUNS=${4:-3}
HOLD=${HOLD:-2m}; LINKS=${LINKS:-10000}
OUTDIR=loadtest/results/$LABEL
mkdir -p "$OUTDIR"

export CACHE_ENABLED=$([ "$CACHE" = on ] && echo true || echo false)
# All k6 traffic comes from one container IP, so the per-IP redirect limit is lifted for the benchmark.
export RATE_LIMIT_REDIRECT_PER_MINUTE=1000000000

psql_q() { docker compose exec -T postgres psql -U postgres -d urlshortener -v ON_ERROR_STOP=1 -At "$@"; }
counter() { curl -sf "localhost:8080/actuator/metrics/$1" | jq '.measurements[0].value'; }
clicks() { psql_q -c "SELECT count(*) AS bench_clicks FROM click_events"; }

# Clicks are written asynchronously (queue -> analytics-consumer), so wait until the backlog has drained.
wait_for_click_writes() {
  local prev=-1 n
  while true; do
    n=$(clicks)
    [ "$n" = "$prev" ] && break
    prev=$n; sleep 3
  done
}

k6_run() {
  docker compose --profile loadtest run --rm -T \
    -e PHASE="$1" -e VUS="$VUS" -e HOLD="$HOLD" -e LINKS="$LINKS" -e OUT="/results/$LABEL/$2" \
    k6 run --quiet /scripts/redirect.js
}

PG_STATS_SQL="
SELECT json_build_object(
  'url_lookup_selects', coalesce(sum(calls) FILTER (WHERE query ILIKE 'select%from url_mappings%'), 0),
  'click_inserts',      coalesce(sum(calls) FILTER (WHERE query ILIKE 'insert into click_events%'), 0),
  'click_count_updates',coalesce(sum(calls) FILTER (WHERE query ILIKE 'update url_mappings%'), 0),
  'total_statements',   coalesce(sum(calls), 0),
  'top', (SELECT json_agg(t) FROM (
            SELECT calls, left(regexp_replace(query, '\s+', ' ', 'g'), 120) AS query
            FROM pg_stat_statements s JOIN pg_database d ON d.oid = s.dbid
            WHERE d.datname = 'urlshortener' AND query NOT ILIKE '%bench_%' AND query NOT ILIKE '%pg_stat_statements%'
            ORDER BY calls DESC LIMIT 8) t))
FROM pg_stat_statements s JOIN pg_database d ON d.oid = s.dbid
WHERE d.datname = 'urlshortener' AND query NOT ILIKE '%bench_%' AND query NOT ILIKE '%pg_stat_statements%'"

jq -n --arg commit "$(git rev-parse --short HEAD)$(git diff --quiet || echo '-dirty')" \
      --arg cpu "$(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m)" \
      --arg host_cores "$(sysctl -n hw.ncpu 2>/dev/null || nproc)" \
      --arg host_mem_gb "$(( $(sysctl -n hw.memsize 2>/dev/null || echo 0) / 1073741824 ))" \
      --arg os "$(sw_vers -productName 2>/dev/null) $(sw_vers -productVersion 2>/dev/null)" \
      --arg docker "$(docker info --format '{{.ServerVersion}}, {{.NCPU}} CPUs, {{.MemTotal}} bytes RAM for containers')" \
      '{commit: $commit, cpu: $cpu, host_cores: $host_cores, host_mem_gb: $host_mem_gb, os: $os, docker: $docker,
        note: "k6 and the whole compose stack run as containers on this one machine"}' > "$OUTDIR/environment.json"

for i in $(seq 1 "$RUNS"); do
  name="cache-$CACHE-run$i"
  echo ">>> $LABEL / $name ($VUS VUs, hold $HOLD)"
  docker compose down -v --remove-orphans >/dev/null 2>&1
  docker compose up -d --wait postgres redis localstack >/dev/null
  docker compose up -d core-api analytics-consumer >/dev/null
  for _ in $(seq 1 120); do curl -sf localhost:8080/actuator/health >/dev/null && break; sleep 1; done

  docker compose exec -T postgres psql -U postgres -d urlshortener -v ON_ERROR_STOP=1 -q -v links="$LINKS" < loadtest/seed.sql

  k6_run warmup warmup.json
  wait_for_click_writes

  psql_q -c "SELECT pg_stat_statements_reset()" >/dev/null
  hits0=$(counter url.cache.hit); miss0=$(counter url.cache.miss); clicks0=$(clicks)

  k6_run main "$name.k6.json"
  t_end=$(date +%s); wait_for_click_writes; drain_s=$(( $(date +%s) - t_end ))

  hits1=$(counter url.cache.hit); miss1=$(counter url.cache.miss); clicks1=$(clicks)
  dropped=$(counter clicks.dropped 2>/dev/null || echo 0)
  pg=$(psql_q -c "$PG_STATS_SQL")

  jq -n --slurpfile k6 "$OUTDIR/$name.k6.json" --argjson pg "$pg" \
        --argjson hits "$(echo "$hits1 - $hits0" | bc)" --argjson misses "$(echo "$miss1 - $miss0" | bc)" \
        --argjson clicks "$(( clicks1 - clicks0 ))" --argjson drain "$drain_s" --arg cache "$CACHE" \
        --argjson dropped "${dropped:-0}" \
        '{cache: $cache, k6: $k6[0], postgres: $pg,
          cache_hits: $hits, cache_misses: $misses,
          cache_hit_rate: (if ($hits + $misses) > 0 then $hits / ($hits + $misses) else null end),
          clicks_recorded: $clicks, clicks_dropped: $dropped, click_write_drain_seconds: $drain}' > "$OUTDIR/$name.json"
  rm "$OUTDIR/$name.k6.json"
  jq -c '{rps: .k6.rps, avg: .k6.latency_ms.avg, p95: .k6.latency_ms.p95, p99: .k6.latency_ms.p99,
          url_selects: .postgres.url_lookup_selects, hit_rate: .cache_hit_rate}' "$OUTDIR/$name.json"
done
docker compose down -v --remove-orphans >/dev/null 2>&1
rm -f "$OUTDIR/warmup.json"
