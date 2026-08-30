#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Start a resource-CAPPED local ArcadeDB in Docker, sized to match the CognoDB
# free tier (0.5 vCPU / 256 MB RAM) as closely as a container allows.
#
# ArcadeDB is multi-model and, IMPORTANTLY, does NOT expose Bolt by default — its
# default protocols are HTTP/Postgres/Redis/Mongo/Gremlin. This script explicitly
# enables the Neo4j-Bolt plugin so the same Neo4j Java driver used for every other
# platform can connect unchanged. Bolt is published on 7689 (7687=Neo4j, 7688=Memgraph).
#
#   ./scripts/local-arcadedb.sh start     # start (or restart) the capped container
#   ./scripts/local-arcadedb.sh stop      # stop + remove it
#   ./scripts/local-arcadedb.sh logs      # follow logs
#
# After 'start', these env vars work with scripts/run.sh:
#   LOCAL_ARCADEDB_URI=bolt://localhost:7689
#   LOCAL_ARCADEDB_USER=root
#   LOCAL_ARCADEDB_PASSWORD=benchpassword
# ---------------------------------------------------------------------------
set -euo pipefail

NAME=gdbench-arcadedb
IMAGE=arcadedata/arcadedb:latest
PASSWORD="${LOCAL_ARCADEDB_PASSWORD:-benchpassword}"
CPUS="${LOCAL_ARCADEDB_CPUS:-0.5}"
MEM="${LOCAL_ARCADEDB_MEM:-256m}"
DB="${LOCAL_ARCADEDB_DB:-graph}"

start() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  echo "Starting $IMAGE capped at --cpus=$CPUS --memory=$MEM (Bolt plugin ON) ..."
  # ArcadeDB's password must satisfy its strength policy; 'benchpassword' is >=8 chars.
  docker run -d --name "$NAME" \
    --cpus="$CPUS" --memory="$MEM" --memory-swap="$MEM" \
    -p 7689:7687 -p 2480:2480 \
    -e JAVA_OPTS="\
-Darcadedb.server.rootPassword=${PASSWORD} \
-Darcadedb.server.defaultDatabases=${DB}[root] \
-Darcadedb.server.plugins=Bolt:com.arcadedb.bolt.BoltProtocolPlugin" \
    "$IMAGE" >/dev/null
  echo -n "Waiting for BOLT on :7689 "
  for _ in $(seq 1 60); do
    # The Bolt listener logs a clear line once it is accepting connections.
    if docker logs "$NAME" 2>&1 | grep -q "Listening for incoming BOLT"; then
      echo " ready."
      echo "  LOCAL_ARCADEDB_URI=bolt://localhost:7689"
      echo "  LOCAL_ARCADEDB_USER=root"
      echo "  LOCAL_ARCADEDB_PASSWORD=$PASSWORD"
      return 0
    fi
    echo -n "."; sleep 2
  done
  echo " timed out waiting for ArcadeDB Bolt listener." >&2
  docker logs --tail 40 "$NAME" >&2 || true
  exit 1
}

case "${1:-start}" in
  start) start ;;
  stop)  docker rm -f "$NAME" >/dev/null 2>&1 && echo "stopped $NAME" || echo "not running" ;;
  logs)  docker logs -f "$NAME" ;;
  *) echo "usage: $0 {start|stop|logs}"; exit 1 ;;
esac
