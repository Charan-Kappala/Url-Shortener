"""Prints a markdown table (mean of runs, with min-max) for every results/<label>/cache-*.json."""
import glob
import json
import os
import sys

root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "results")

ROWS = [
    ("Throughput (req/s)", lambda r: r["k6"]["rps"], "{:.0f}"),
    ("Avg latency (ms)", lambda r: r["k6"]["latency_ms"]["avg"], "{:.1f}"),
    ("p95 latency (ms)", lambda r: r["k6"]["latency_ms"]["p95"], "{:.1f}"),
    ("p99 latency (ms)", lambda r: r["k6"]["latency_ms"]["p99"], "{:.1f}"),
    ("Requests", lambda r: r["k6"]["requests"], "{:.0f}"),
    ("Unexpected status rate", lambda r: r["k6"]["unexpected_status_rate"] * 100, "{:.2f}%"),
    ("Postgres url_mappings SELECTs", lambda r: r["postgres"]["url_lookup_selects"], "{:.0f}"),
    ("Postgres statements (all)", lambda r: r["postgres"]["total_statements"], "{:.0f}"),
    ("Cache hit rate", lambda r: (r["cache_hit_rate"] or 0) * 100, "{:.1f}%"),
]

for label_dir in sorted(glob.glob(os.path.join(root, "*"))):
    groups = {}
    for f in sorted(glob.glob(os.path.join(label_dir, "cache-*-run*.json"))):
        run = json.load(open(f))
        groups.setdefault(run["cache"], []).append(run)
    if not groups:
        continue
    vus = next(iter(groups.values()))[0]["k6"]["vus"]
    print(f"\n### {os.path.basename(label_dir)} ({vus} VUs)\n")
    cols = sorted(groups)
    print("| Metric | " + " | ".join(f"cache {c} (n={len(groups[c])})" for c in cols) + " |")
    print("|---|" + "---|" * len(cols))
    for name, get, fmt in ROWS:
        cells = []
        for c in cols:
            vals = [get(r) for r in groups[c]]
            mean = sum(vals) / len(vals)
            cells.append(f"{fmt.format(mean)} ({fmt.format(min(vals))}–{fmt.format(max(vals))})")
        print(f"| {name} | " + " | ".join(cells) + " |")
