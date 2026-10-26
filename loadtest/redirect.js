/**
 * k6 load test for GET /{shortCode}.
 *
 * Access pattern:
 *  - LINKS short links exist (seeded by seed.sql as l000001 .. l0NNNNN).
 *  - Link popularity follows a Zipf curve with exponent ZIPF_S: the k-th most popular link
 *    gets traffic proportional to 1/k^s. With 10,000 links and s=1.0 the top 100 links
 *    (1%) receive about half of all requests.
 *  - MISS_RATIO of requests ask for codes that do not exist (typos, scanners) and expect 404.
 *
 * Closed model: VUS virtual users loop request -> think time (0-500 ms, uniform).
 * PHASE=warmup ramps 0 -> VUS over RAMP; PHASE=main holds VUS for HOLD and writes the summary.
 * Normally driven by run-bench.sh, which runs warmup and main back to back on a fresh stack.
 */
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL   = __ENV.BASE_URL   || 'http://core-api:8080'
const LINKS      = parseInt(__ENV.LINKS || '10000')
const ZIPF_S     = parseFloat(__ENV.ZIPF_S || '1.0')
const MISS_RATIO = parseFloat(__ENV.MISS_RATIO || '0.02')
const VUS        = parseInt(__ENV.VUS || '100')
const PHASE      = __ENV.PHASE || 'main'
const RAMP       = __ENV.RAMP || '30s'
const HOLD       = __ENV.HOLD || '2m'
const OUT        = __ENV.OUT || '/results/summary.json'

// Cumulative distribution over link ranks, built once per VU in the init context.
const cdf = new Float64Array(LINKS)
{
  let total = 0
  for (let k = 1; k <= LINKS; k++) total += 1 / Math.pow(k, ZIPF_S)
  let acc = 0
  for (let k = 1; k <= LINKS; k++) {
    acc += 1 / Math.pow(k, ZIPF_S) / total
    cdf[k - 1] = acc
  }
}

function zipfRank() {
  const u = Math.random()
  let lo = 0, hi = LINKS - 1
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if (cdf[mid] < u) lo = mid + 1
    else hi = mid
  }
  return lo + 1
}

const linkCode = (n) => 'l' + String(n).padStart(6, '0')

export const options = {
  scenarios: PHASE === 'warmup'
    ? { warmup: { executor: 'ramping-vus', startVUs: 0, stages: [{ duration: RAMP, target: VUS }], gracefulRampDown: '0s' } }
    : { main: { executor: 'constant-vus', vus: VUS, duration: HOLD, gracefulStop: '5s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: true,
}

export default function () {
  const unknown = Math.random() < MISS_RATIO
  const code = unknown ? 'x' + Math.floor(Math.random() * 1e6) : linkCode(zipfRank())

  const res = http.get(`${BASE_URL}/${code}`, {
    redirects: 0,
    tags: { name: 'redirect', kind: unknown ? 'unknown' : 'known' },
  })

  check(res, {
    'expected status': (r) => r.status === (unknown ? 404 : 302),
  })

  sleep(Math.random() * 0.5)
}

export function handleSummary(data) {
  if (PHASE === 'warmup') return { stdout: '' }
  const m = data.metrics
  const d = m.http_req_duration.values
  const summary = {
    vus: VUS,
    hold: HOLD,
    links: LINKS,
    zipf_s: ZIPF_S,
    miss_ratio: MISS_RATIO,
    requests: m.http_reqs.values.count,
    rps: m.http_reqs.values.rate,
    latency_ms: { avg: d.avg, med: d.med, p90: d['p(90)'], p95: d['p(95)'], p99: d['p(99)'], max: d.max },
    unexpected_status_rate: 1 - m.checks.values.rate,
  }
  return {
    [OUT]: JSON.stringify(summary, null, 2),
    stdout: JSON.stringify(summary, null, 2) + '\n',
  }
}
