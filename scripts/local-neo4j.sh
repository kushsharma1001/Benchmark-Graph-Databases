#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Start a resource-CAPPED local Neo4j in Docker, sized to match the CognoDB
# free tier (0.5 vCPU / 256 MB RAM) as closely as a container allows. This is
# the self-hosted control target and the one the harness is validated against
# without spending cloud quota.
#
#   ./scripts/local-neo4j.sh start     # start (or restart) the capped container
#   ./scripts/local-neo4j.sh stop      # stop + remove it
#   ./scripts/local-neo4j.sh logs      # follow logs
#
# After 'start', these env vars work with scripts/run.sh:
#   LOCAL_NEO4J_URI=bolt://localhost:7687
#   LOCAL_NEO4J_USER=neo4j
#   LOCAL_NEO4J_PASSWORD=benchpassword
# ---------------------------------------------------------------------------
set -euo pipefail

NAME=gdbench-neo4j
IMAGE=neo4j:5.26-community
PASSWORD="${LOCAL_NEO4J_PASSWORD:-benchpassword}"
CPUS="${LOCAL_NEO4J_CPUS:-0.5}"
MEM="${LOCAL_NEO4J_MEM:-256m}"

start() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  echo "Starting $IMAGE capped at --cpus=$CPUS --memory=$MEM ..."
  docker run -d --name "$NAME" \
    --cpus="$CPUS" --memory="$MEM" --memory-swap="$MEM" \
    -p 7687:7687 -p 7474:7474 \
    -e NEO4J_AUTH="neo4j/${PASSWORD}" \
    -e NEO4J_server_memory_heap_initial__size=96m \
    -e NEO4J_server_memory_heap_max__size=96m \
    -e NEO4J_server_memory_pagecache_size=64m \
    "$IMAGE" >/dev/null
  echo -n "Waiting for Bolt on :7687 "
  for _ in $(seq 1 60); do
    if docker exec "$NAME" cypher-shell -u neo4j -p "$PASSWORD" "RETURN 1;" >/dev/null 2>&1; then
      echo " ready."
      echo "  LOCAL_NEO4J_URI=bolt://localhost:7687"
      echo "  LOCAL_NEO4J_USER=neo4j"
      echo "  LOCAL_NEO4J_PASSWORD=$PASSWORD"
      return 0
    fi
    echo -n "."; sleep 2
  done
  echo " timed out waiting for Neo4j." >&2
  docker logs --tail 40 "$NAME" >&2 || true
  exit 1
}

case "${1:-start}" in
  start) start ;;
  stop)  docker rm -f "$NAME" >/dev/null 2>&1 && echo "stopped $NAME" || echo "not running" ;;
  logs)  docker logs -f "$NAME" ;;
  *) echo "usage: $0 {start|stop|logs}"; exit 1 ;;
esac
