#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# One-command benchmark runner.
#
#   ./scripts/run.sh all                 # load + bench every platform with creds
#   ./scripts/run.sh all --platform cognodb
#   ./scripts/run.sh load --platform aura-free
#   ./scripts/run.sh bench --platform cognodb
#   ./scripts/run.sh report
#
# Credentials are read from the environment. If a `.env` file exists in the repo
# root it is sourced automatically (it is git-ignored — never commit secrets).
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  set -a; source .env; set +a
  echo "Loaded credentials from .env"
fi

# Build the fat jar once if it is missing, then run it. Using the jar (rather
# than exec:java) keeps startup fast and the classpath reproducible.
JAR=target/gdbench.jar
if [[ ! -f "$JAR" ]]; then
  echo "Building $JAR ..."
  ./mvnw -q -DskipTests package
fi

exec java -jar "$JAR" "$@"
