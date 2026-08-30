#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Start a resource-CAPPED local Memgraph in Docker on port 7688 (so it can run
# alongside the local Neo4j on 7687). In-memory control target.
#
#   ./scripts/local-memgraph.sh start | stop | logs
#
# Env for scripts/run.sh:
#   LOCAL_MEMGRAPH_URI=bolt://localhost:7688
#   LOCAL_MEMGRAPH_USER=          (empty — no auth by default)
#   LOCAL_MEMGRAPH_PASSWORD=
# ---------------------------------------------------------------------------
set -euo pipefail

NAME=gdbench-memgraph
IMAGE=memgraph/memgraph:2.18.1
CPUS="${LOCAL_MEMGRAPH_CPUS:-0.5}"
MEM="${LOCAL_MEMGRAPH_MEM:-256m}"

start() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  echo "Starting $IMAGE capped at --cpus=$CPUS --memory=$MEM ..."
  docker run -d --name "$NAME" \
    --cpus="$CPUS" --memory="$MEM" --memory-swap="$MEM" \
    -p 7688:7687 \
    "$IMAGE" --telemetry-enabled=false >/dev/null
  echo "Started. Bolt on bolt://localhost:7688 (no auth)."
  echo "  LOCAL_MEMGRAPH_URI=bolt://localhost:7688"
}

case "${1:-start}" in
  start) start ;;
  stop)  docker rm -f "$NAME" >/dev/null 2>&1 && echo "stopped $NAME" || echo "not running" ;;
  logs)  docker logs -f "$NAME" ;;
  *) echo "usage: $0 {start|stop|logs}"; exit 1 ;;
esac
