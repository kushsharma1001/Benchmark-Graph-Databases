#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Optional: download a real public SNAP dataset for the `csv` load mode.
#
# By default the harness uses a deterministic SYNTHETIC social graph (no
# download needed, byte-for-byte reproducible across platforms). If you prefer
# a real-world graph, fetch one here and point benchmark.yaml at it:
#
#   ./scripts/download-dataset.sh soc-pokec     # ~30M edges (TOO BIG for free tiers)
#   ./scripts/download-dataset.sh ego-facebook  # ~88k edges (small, for smoke tests)
#   ./scripts/download-dataset.sh wiki-vote      # ~103k edges (fits free tiers)
#
# Then set in config/benchmark.yaml:
#   dataset:
#     mode: csv
#     edgeFile: data/<file>.txt
#
# NOTE: soc-Pokec has ~30M edges — far over the free-tier budget. Sample it down
# first (e.g. `head -n 300000`) or use wiki-Vote which already fits.
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p data

case "${1:-wiki-vote}" in
  soc-pokec)
    URL="https://snap.stanford.edu/data/soc-pokec-relationships.txt.gz"
    OUT="data/soc-pokec-relationships.txt" ;;
  ego-facebook)
    URL="https://snap.stanford.edu/data/facebook_combined.txt.gz"
    OUT="data/facebook_combined.txt" ;;
  wiki-vote)
    URL="https://snap.stanford.edu/data/wiki-Vote.txt.gz"
    OUT="data/wiki-Vote.txt" ;;
  *) echo "unknown dataset: $1"; exit 1 ;;
esac

echo "Downloading $URL ..."
curl -fL "$URL" -o "${OUT}.gz"
gunzip -f "${OUT}.gz"
echo "Saved $OUT"
echo "Edge count: $(grep -cv '^#' "$OUT")"
echo "Set dataset.mode=csv and dataset.edgeFile=$OUT in config/benchmark.yaml"
