# A Fair Graph Database Benchmark

This project tries to benchmark **CognoDB Cloud** against five other Bolt/Cypher graph databases on the **same dataset**, the **same queries**, and **matched resources** — and to report **percentiles, not averages**. My goal wasn't to crown a winner; it was to be fair,
reproducible in one command, and honest about every caveat. I ran all six platforms
end-to-end with zero errors, and every number here is real.

**Why 3 cloud + 3 local:** Bolt-native, genuinely free-tier graph *clouds* are scarce — the
ecosystem is essentially the Neo4j family plus Memgraph. Beyond CognoDB itself I could only
find **two** free-tier cloud databases to compare against (Neo4j Aura Free and Memgraph
Cloud). So rather than stop at three cloud platforms, I added **three resource-capped local
Docker engines** (Neo4j 5, Memgraph, ArcadeDB) for engine diversity — giving **6 platforms in
total: 3 cloud + 3 local**.

**The one caveat that governs everything:** I drove the benchmark from a GitHub Codespace,
so the three cloud platforms pay client↔cloud network round-trip (~100–300 ms) on every
query while the three local engines answer over `localhost`. That is why cloud read p50s
sit in the hundreds of ms and local ones are sub-millisecond — it's distance, not engine
speed. So I only compare **cloud-to-cloud** and **local-to-local** (see [Analysis](#analysis)).

---

## What I measure

Every read workload runs **≥120 measured iterations after a 30-iteration warm-up**, and I
report **percentiles (p50/p95/p99), not just averages**. Full numbers are in
[Results](#results) below.

| Category | Metric | Reported |
|---|---|---|
| Data loading | ingest throughput | nodes/s, rels/s, total wall-clock |
| Traversals | 1/2/3-hop latency | p50 & p95 (ms) from a fixed random start-node set |
| Lookups | point + indexed lookup | p50 & p95 (ms); indexed property stated |
| Aggregation | `COUNT` over `:FOLLOWS` | p50 / p95 / p99 (ms) |
| Mixed workload | concurrent read/write | ops/s + p95 at 1/10/40 clients (90/10 read/write) |
| Footprint | resource usage where observable | stored counts; "not observable" where hidden |

## How I keep it fair

Every platform speaks **Bolt + Cypher**, so I point **one** driver (the official Neo4j Java
driver) at all of them and run **byte-identical query text**. Only the connection URI
changes. That single choice removes "different client library" as a confound.

- **Free/entry tier everywhere.** I cap the self-hosted controls with Docker `--cpus=0.5`
  and `--memory` to match CognoDB's tiny free tier (0.5 vCPU / 256 MB / 1 GB).
- **One deterministic dataset** — 30k `:Person` nodes and 150k `:FOLLOWS` relationships,
  seed `42`, preferential attachment. Fits Aura Free's 400k cap; regenerates identically,
  no download.
- **I record each platform's real specs and every place tier parity is imperfect.** Free
  tiers aren't identical hardware; I document the gaps instead of hiding them.

| Platform | Tier | Engine | vCPU | RAM |
|---|---|---|---|---|
| CognoDB Cloud | Free (c0) | Bolt/Cypher | 0.5 | 256 MB |
| Neo4j Aura Free | AuraDB Free | Bolt/Cypher | 1 | ~1 GB (capacity-capped) |
| Memgraph Cloud | Free trial | Bolt/Cypher (in-memory) | 1 | 2 GB |
| Neo4j 5 (local) | capped | Bolt/Cypher | `--cpus=0.5` | `--memory=2g`¹ |
| Memgraph (local) | capped | Bolt/Cypher (in-memory) | `--cpus=0.5` | `--memory=256m` |
| ArcadeDB (local) | capped | Bolt/Cypher (translated) | `--cpus=0.5` | `--memory=2g`¹ |

> ¹ **A finding, not a footnote:** stock Neo4j 5 won't boot at 256 MB and was even
> OOM-killed at 1 GB — only 2 GB is stable. ArcadeDB is likewise unreliable at 256 MB.
> Memgraph (C++) boots healthy at 256 MB. A managed engine serving queries at 256 MB is
> doing real engineering a stock JVM container can't. So the Neo4j/ArcadeDB columns are 2 GB
> controls — I keep CPU capped at 0.5 for all three (CPU is the lever that shapes latency).

I add **ArcadeDB** (a multi-model engine with a Neo4j-Bolt plugin) as a local control for
engine diversity — the identical driver and Cypher run against a completely different
storage engine unchanged.

## Quick start

**Prereqs:** JDK 17+ (the bundled `./mvnw` fetches Maven) and Docker (for the local
controls). Credentials are **never committed** — `config/application.properties` ships with
empty `${NAME}` placeholders and I inject real values as environment variables at runtime. A
platform with no credentials is simply **skipped**.

**1. Export credentials** (single-quote them — passwords often contain `!` or `+`). The
local values are the non-secret defaults from `docker-compose.yml`:

```bash
export COGNODB_URI='<your-cognodb-uri>'          COGNODB_USER='<user>'          COGNODB_PASSWORD='<pw>'
export AURA_URI='<your-aura-uri>'                AURA_USER='<user>'             AURA_PASSWORD='<pw>'
export MEMGRAPH_CLOUD_URI='bolt+ssc://<host>:7687'  MEMGRAPH_CLOUD_USER='<user>'  MEMGRAPH_CLOUD_PASSWORD='<pw>'
export LOCAL_NEO4J_URI='bolt://localhost:7687'   LOCAL_NEO4J_USER='neo4j'       LOCAL_NEO4J_PASSWORD='benchpassword'
export LOCAL_MEMGRAPH_URI='bolt://localhost:7688' LOCAL_MEMGRAPH_USER=''        LOCAL_MEMGRAPH_PASSWORD=''
export LOCAL_ARCADEDB_URI='bolt://localhost:7689' LOCAL_ARCADEDB_USER='root'    LOCAL_ARCADEDB_PASSWORD='benchpassword'
```

**2. Start the local databases:**

```bash
docker compose up -d
```

**3. Confirm the JVM engines opened Bolt** (~1–2 min on 0.5 vCPU):

Docker containers will spin up local docker images. So, we will have to wait until they are up. Wait for ~2 mins and execute below commands to see the connections live.

```bash
docker exec gdbench-neo4j cypher-shell -u neo4j -p benchpassword "RETURN 1;"
docker compose logs arcadedb | grep -i "Listening for incoming BOLT"
```

**4. Run everything** (loads, benchmarks, writes `results/RESULTS.md` + `results.csv`):

```bash
./scripts/run.sh all
```

**5. Tear down:**

```bash
docker compose down
```

`run.sh` builds `target/gdbench.jar` on first use, then runs `java -jar target/gdbench.jar all`.
`all` clears `results/` first, so it reflects only the current run.

## Results

This is my real 6-platform run — 30k nodes / 150k rels, **120 measured iterations** per read
workload after 30 warm-up iterations, warm, **0 errors on every workload**. The same tables
are auto-generated into **[`results/RESULTS.md`](results/RESULTS.md)** (regenerate with
`./scripts/run.sh report`), and `results/results.csv` is long-format for charting.

> ⚠️ Cloud rows (CognoDB, Aura, Memgraph Cloud) are **network-round-trip-bound** — driven
> from a Codespace, not co-located. Their latencies are wire time, not engine time. Compare
> clouds only to each other and local engines only to each other (see [Analysis](#analysis)).

### Data loading (ingest throughput)

| Platform | Nodes/s | Rels/s | Load time (s) |
|---|--:|--:|--:|
| CognoDB Cloud | 1109.99 | 5549.94 | 27.03 |
| Neo4j Aura Free | 1903.89 | 9519.43 | 15.76 |
| Memgraph Cloud | 2615.81 | 13079.03 | 11.47 |
| Neo4j 5 (local) | 258.83 | 1294.15 | 115.91 |
| Memgraph (local) | **18444.48** | **92222.41** | **1.63** |
| ArcadeDB (local) | 646.62 | 3233.10 | 46.40 |

### Traversal latency (p50 / p95 ms)

| Platform | 1-hop p50 | 1-hop p95 | 2-hop p50 | 2-hop p95 | 3-hop p50 | 3-hop p95 |
|---|--:|--:|--:|--:|--:|--:|
| CognoDB Cloud | 207.18 | 414.21 | 206.98 | 413.26 | 411.23 | 412.65 |
| Neo4j Aura Free | 111.84 | 113.50 | 111.60 | 113.48 | 111.51 | 113.76 |
| Memgraph Cloud | 264.74 | 274.26 | 264.61 | 265.34 | 264.72 | 267.22 |
| Neo4j 5 (local) | 3.31 | 93.28 | 4.20 | 97.40 | 2.96 | 91.87 |
| Memgraph (local) | **0.51** | **1.58** | **0.52** | **0.95** | **0.60** | **1.94** |
| ArcadeDB (local) | 1.44 | 60.65 | 2.02 | 63.40 | 1.71 | 77.66 |

### Lookups (p50 / p95 ms)

All platforms index the same property: **`Person.id` (range index)**, created before edge load.

| Platform | Point p50 | Point p95 | Indexed p50 | Indexed p95 | Indexed on |
|---|--:|--:|--:|--:|---|
| CognoDB Cloud | 210.11 | 420.12 | 434.29 | 437.31 | `Person.id` |
| Neo4j Aura Free | 111.10 | 111.75 | 111.20 | 113.47 | `Person.id` |
| Memgraph Cloud | 264.56 | 267.11 | 264.63 | 265.44 | `Person.id` |
| Neo4j 5 (local) | 1.94 | 90.29 | 3.59 | 96.68 | `Person.id` |
| Memgraph (local) | **0.52** | **1.52** | **0.56** | **1.59** | `Person.id` |
| ArcadeDB (local) | 0.74 | 1.94 | 86.60 | 204.81 | `Person.id` |

### Aggregation (p50 / p95 / p99 ms) — `COUNT` of all `:FOLLOWS`

| Platform | p50 | p95 | p99 |
|---|--:|--:|--:|
| CognoDB Cloud | 587.28 | 607.31 | 650.90 |
| Neo4j Aura Free | 110.87 | 112.22 | 223.72 |
| Memgraph Cloud | 292.83 | 304.88 | 382.05 |
| Neo4j 5 (local) | **1.78** | 89.37 | 91.30 |
| Memgraph (local) | 16.95 | 68.82 | 75.28 |
| ArcadeDB (local) | 492.12 | 580.65 | 612.92 |

### Mixed read/write workload — throughput (ops/s) & per-op p95 (ms)

90% reads / 10% writes, swept across 1 / 10 / 40 concurrent clients over a fixed 20 s window.

| Platform | 1 client ops/s | p95 | 10 clients ops/s | p95 | 40 clients ops/s | p95 |
|---|--:|--:|--:|--:|--:|--:|
| CognoDB Cloud | 4.08 | 419.90 | 41.06 | 415.48 | 149.57 | 420.18 |
| Neo4j Aura Free | 8.86 | 119.87 | 87.10 | 120.24 | 341.98 | 126.73 |
| Memgraph Cloud | 4.25 | 265.94 | 40.01 | 270.12 | 160.90 | 265.16 |
| Neo4j 5 (local) | 51.45 | 92.08 | 212.37 | 98.69 | 279.74 | 287.98 |
| Memgraph (local) | **1789.06** | 0.75 | **2543.85** | 4.26 | **2969.83** | 73.29 |
| ArcadeDB (local) | 483.13 | 1.73 | 870.83 | 77.69 | 1359.42 | 91.95 |

### Footprint

| Platform | Nodes stored | Rels stored | Stored size | Memory |
|---|--:|--:|---|---|
| CognoDB Cloud | 30000 | 149890 | not observable from client | not observable from client |
| Neo4j Aura Free | 30000 | 149890 | not observable from client | not observable from client |
| Memgraph Cloud | 30000 | 149890 | not observable from client | not observable from client |
| Neo4j 5 (local) | 30000 | 149890 | not observable from client | not observable from client |
| Memgraph (local) | 30000 | 149890 | not observable from client | not observable from client |
| ArcadeDB (local) | 30000 | 149890 | not observable from client | not observable from client |

> Stored size and memory usage are **not observable from a Bolt client** on any of these
> platforms — the managed clouds hide it and the local engines don't expose it over the wire;
> check each platform's console/`docker stats` for the host-side view. Local server versions:
> Memgraph 5.9.0 (community), Neo4j Kernel 5.26.30 (community). Charts: drop
> `results/results.csv` (long-format `platform,category,metric,value`) into any plotting tool,
> or run `python3 scripts/plot.py` once you have ≥2 platforms.

## Methodology

- **Warm up, then measure.** 30 warm-up iterations discarded, then ≥120 timed (brief asks
  ≥100), percentiles via nearest-rank.
- **Fixed random start nodes** from a seeded RNG (seed `7`), reset before the measured loop,
  so every platform answers the identical questions.
- **Percentiles, not averages** — the tail is where free-tier throttling and GC pauses show
  up; a mean hides them.
- **Concurrency sweep** — the mixed workload runs 1/10/40 clients for a fixed window, 90%
  reads / 10% writes, writing a disposable `TEMP_BENCH_EDGE` (cleaned up) so it never
  pollutes the `:FOLLOWS` graph.
- **Load with `UNWIND $rows`** (10k/batch), `:Person(id)` index created before edges so the
  `MATCH`-by-id is index-backed. `MERGE` keeps loads idempotent (slower than `CREATE`, but
  re-runnable). Reported numbers are warm.

## Analysis

**Cloud vs cloud.** Aura was fastest and steadiest on reads (p50≈p95 ≈ 111 ms → a stable
network path); CognoDB and Memgraph Cloud both sit higher. CognoDB's read p95 blows out to
~430 ms (2× its p50), pointing to a more variable path / burstier tier rather than a slow
engine. Aggregation is the one place engine cost peeks through the wire (CognoDB 587 ms vs
Aura 111 ms), but I can't cleanly separate engine from network remotely — a same-region
re-run is required before I'd call it an engine result.

**Local vs local** (no network in the way):

- **Memgraph (in-memory, C++) dominates** — sub-ms reads, ~1.6 s to ingest 150k rels, and
  ~10× Neo4j's mixed throughput at 40 clients — all in 256 MB while the JVM engines needed
  2 GB. That efficiency is a real result.
- **Neo4j wins `COUNT` decisively** (1.78 ms vs Memgraph 17 ms, ArcadeDB 492 ms) — its
  planner lowers a full relationship count to a native aggregate. Its ingest is slowest
  (116 s): `MERGE` on half a core with a small page cache is punishing.
- **ArcadeDB is a study in contrasts** — sub-ms point/1-hop reads, but range scans (87 ms)
  and `COUNT` (492 ms) are orders of magnitude slower because its Cypher is a translation
  layer, not a native planner. That surfaced *only* because the query text was byte-identical
  across engines.
- **The p95≫p50 tail on the JVM engines is the CPU cap** — half a core means occasional
  scheduler/GC stalls stretch the tail while the median stays low. Memgraph's tail stays
  tight. This is exactly why percentiles are mandatory.

**Next step for a clean CognoDB verdict:** re-run from a VM co-located with each cloud's
region to remove the RTT term and turn these network observations into engine comparisons.

## Caveats

- **JVM engines needed 2 GB; Memgraph and CognoDB serve queries in 256 MB.** Neo4j was
  OOM-killed at both 256 MB and 1 GB (it opened Bolt, then died ~2 min later); only 2 GB is
  stable. So Neo4j/ArcadeDB columns aren't RAM-comparable to CognoDB's 256 MB tier — but CPU
  is capped at 0.5 for all three.
- **The clouds look slow because of the network, not the engine.** Every cloud latency
  includes ~100–300 ms client↔cloud RTT from the Codespace. Compare cloud-to-cloud and
  local-to-local only; re-run in-region for a fair cloud-vs-local read.
- **Free tiers aren't identical hardware.** Aura Free is capacity-capped (200k/400k), not
  vCPU/RAM-published; Memgraph's trial advertises more RAM than CognoDB's c0. I use each
  vendor's smallest tier and flag the mismatch.
- **ArcadeDB specifics.** Bolt is off by default (I enable the `BoltProtocolPlugin`); its
  Cypher is translated, so `dbms.components` is unimplemented (`serverInfo` shows unknown)
  and aggregation/range-scans run slow. Its image ships no `bash`, so my TCP healthcheck
  reported a false "unhealthy" while Bolt served fine — I removed the healthcheck and confirm
  readiness via the Bolt log line above.
- **`MERGE` vs `CREATE`** on load — I accept the slowdown for idempotent, repeatable loads.

## Harness architecture

```
ai.wexa.gdbench
├── Main                 picocli CLI: load / bench / all / report
├── config/              PlatformConfig, BenchmarkConfig, Configs (YAML + env creds)
├── db/                  GraphDatabaseClient — one Bolt driver, any platform
├── dataset/             EdgeStream (synthetic or SNAP CSV) + DataLoader (batched UNWIND)
├── workload/            ReadWorkloads, MixedWorkload, BenchmarkRunner
├── metrics/             LatencyStats (p50/p95/p99), Timer, BenchmarkResult (JSON model)
└── report/              ResultStore (JSON I/O), ReportGenerator (Markdown + CSV)
```

Every platform is reached through one `GraphDatabaseClient` over the Neo4j Bolt driver, so
adding a platform is a config edit in `config/platforms.yaml`, not a code change. ArcadeDB
proved the seam — a brand-new engine added with just a start script + registry entry, no
harness code touched. Config lives in `config/platforms.yaml` (registry + specs) and
`config/benchmark.yaml` (dataset size, iterations, concurrency). Secrets come only from
`<PREFIX>_URI/_USER/_PASSWORD` env vars.

## Reproducing on your own accounts

1. **CognoDB:** sign up, create a free c0 instance, export `COGNODB_URI/_USER/_PASSWORD`.
2. **Neo4j Aura Free:** create an instance, export `AURA_*`.
3. **Memgraph Cloud:** create a free-trial project, export `MEMGRAPH_CLOUD_*` (`bolt+ssc://`).
4. **Local controls:** `docker compose up -d` (caps CPU/RAM per platform).
5. **Run from the same region** as your cloud instances for comparable latency, then
   `./scripts/run.sh all` and commit `results/*.json` + the regenerated `RESULTS.md`.

Everything needed to re-run is here and in the two YAML files. No hidden state, and **no
credentials are stored in this repository.**
