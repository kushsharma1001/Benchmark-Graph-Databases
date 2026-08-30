#!/usr/bin/env python3
"""
Render charts from results/results.csv (long format: platform,category,metric,value).

Usage:
    python3 scripts/plot.py                 # reads results/results.csv
    python3 scripts/plot.py path/to.csv

Produces PNGs under results/charts/. Requires matplotlib (pip install matplotlib).
This is a convenience for the README "charts are a plus" — the benchmark itself has
no Python dependency; all measurement is done by the Java harness.
"""
import csv
import os
import sys
from collections import defaultdict

def main() -> int:
    csv_path = sys.argv[1] if len(sys.argv) > 1 else "results/results.csv"
    if not os.path.exists(csv_path):
        print(f"No results CSV at {csv_path}. Run './scripts/run.sh report' first.")
        return 1

    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("matplotlib not installed. Run: pip install matplotlib")
        return 1

    # metric -> platform -> value
    data = defaultdict(dict)
    with open(csv_path, newline="") as f:
        for row in csv.DictReader(f):
            key = f"{row['category']}:{row['metric']}"
            try:
                data[key][row["platform"]] = float(row["value"])
            except ValueError:
                pass

    out_dir = "results/charts"
    os.makedirs(out_dir, exist_ok=True)

    # One grouped bar chart per interesting metric family.
    families = {
        "Traversal p95 (ms, lower=better)": [k for k in data if k.startswith("traversal") and k.endswith("p95")],
        "Mixed throughput (ops/s, higher=better)": [k for k in data if "ops_per_sec" in k],
        "Lookup p95 (ms, lower=better)": [k for k in data if k.startswith("lookup") and k.endswith("p95")],
    }

    for title, keys in families.items():
        if not keys:
            continue
        platforms = sorted({p for k in keys for p in data[k]})
        if not platforms:
            continue
        fig, ax = plt.subplots(figsize=(10, 5))
        width = 0.8 / max(1, len(keys))
        for i, k in enumerate(sorted(keys)):
            xs = range(len(platforms))
            ys = [data[k].get(p, 0) for p in platforms]
            ax.bar([x + i * width for x in xs], ys, width, label=k.split(":", 1)[1])
        ax.set_title(title)
        ax.set_xticks([x + width * (len(keys) - 1) / 2 for x in range(len(platforms))])
        ax.set_xticklabels(platforms, rotation=20, ha="right")
        ax.legend(fontsize=8)
        fig.tight_layout()
        fname = os.path.join(out_dir, title.split(" ")[0].lower() + ".png")
        fig.savefig(fname, dpi=120)
        print(f"wrote {fname}")

    return 0

if __name__ == "__main__":
    raise SystemExit(main())
