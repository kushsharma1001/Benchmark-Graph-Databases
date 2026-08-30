# Benchmark Results Matrix

Generated from 6 platform result file(s). Latencies in **milliseconds**; lower is better. Throughput in **ops/sec**; higher is better.

## Platforms & tiers

| Platform | Tier | Engine | vCPU | RAM | Disk | Server |
|---|---|---|---|---|---|---|
| Neo4j Aura Free | Free (AuraDB Free) | Bolt / Cypher | shared (not published) | ~1 GB (not published) | up to 200k nodes / 400k rels (capacity-limited, not disk-sized) | unknown (dbms.components unavailable) |
| CognoDB Cloud | Free (c0) | Bolt / Cypher | 0.5 (burstable) | 256 MB | 1 GB | unknown (dbms.components unavailable) |
| ArcadeDB (local Docker, capped) | Self-hosted, capped to free-tier resources | Bolt / Cypher (multi-model, translated) | 0.5 (--cpus=0.5) | 2 GB (--memory=2g; unreliable at 256m — see README §8) | container volume (host disk) | null |
| Memgraph (local Docker, capped) | Self-hosted, capped to free-tier resources | Bolt / Cypher (in-memory) | 0.5 (--cpus=0.5) | 256 MB (--memory=256m) | in-memory | Memgraph 5.9.0 (community) |
| Neo4j 5 (local Docker, capped) | Self-hosted, capped to free-tier resources | Bolt / Cypher | 0.5 (--cpus=0.5) | 2 GB (--memory=2g; OOM-killed at 256m AND 1g — see README §8) | container volume (host disk) | Neo4j Kernel 5.26.30 (community) |
| Memgraph Cloud | Free trial | Bolt / Cypher (in-memory) | 1 (trial) | 2 GB (trial) | in-memory + on-disk durability | unknown (dbms.components unavailable) |

## Data loading (ingest throughput)

| Platform | Nodes | Rels | Load time (s) | Nodes/s | Rels/s |
|---|--:|--:|--:|--:|--:|
| Neo4j Aura Free | 30000 | 150000 | 15.76 | 1903.89 | 9519.43 |
| CognoDB Cloud | 30000 | 150000 | 27.03 | 1109.99 | 5549.94 |
| ArcadeDB (local Docker, capped) | 30000 | 150000 | 46.40 | 646.62 | 3233.10 |
| Memgraph (local Docker, capped) | 30000 | 150000 | 1.63 | 18444.48 | 92222.41 |
| Neo4j 5 (local Docker, capped) | 30000 | 150000 | 115.91 | 258.83 | 1294.15 |
| Memgraph Cloud | 30000 | 150000 | 11.47 | 2615.81 | 13079.03 |

## Traversal latency (p50 / p95 ms)

| Platform | 1-hop p50 | 1-hop p95 | 2-hop p50 | 2-hop p95 | 3-hop p50 | 3-hop p95 |
|---|--:|--:|--:|--:|--:|--:|
| Neo4j Aura Free | 111.84 | 113.50 | 111.60 | 113.48 | 111.51 | 113.76 |
| CognoDB Cloud | 207.18 | 414.21 | 206.98 | 413.26 | 411.23 | 412.65 |
| ArcadeDB (local Docker, capped) | 1.44 | 60.65 | 2.02 | 63.40 | 1.71 | 77.66 |
| Memgraph (local Docker, capped) | 0.51 | 1.58 | 0.52 | 0.95 | 0.60 | 1.94 |
| Neo4j 5 (local Docker, capped) | 3.31 | 93.28 | 4.20 | 97.40 | 2.96 | 91.87 |
| Memgraph Cloud | 264.74 | 274.26 | 264.61 | 265.34 | 264.72 | 267.22 |

## Lookups (p50 / p95 ms)

| Platform | Point p50 | Point p95 | Indexed p50 | Indexed p95 | Indexed on |
|---|--:|--:|--:|--:|---|
| Neo4j Aura Free | 111.10 | 111.75 | 111.20 | 113.47 | Person.id (range index) |
| CognoDB Cloud | 210.11 | 420.12 | 434.29 | 437.31 | Person.id (range index) |
| ArcadeDB (local Docker, capped) | 0.74 | 1.94 | 86.60 | 204.81 | Person.id (range index) |
| Memgraph (local Docker, capped) | 0.52 | 1.52 | 0.56 | 1.59 | Person.id (range index) |
| Neo4j 5 (local Docker, capped) | 1.94 | 90.29 | 3.59 | 96.68 | Person.id (range index) |
| Memgraph Cloud | 264.56 | 267.11 | 264.63 | 265.44 | Person.id (range index) |

## Aggregation (p50 / p95 ms)

| Platform | Query | p50 | p95 | p99 |
|---|---|--:|--:|--:|
| Neo4j Aura Free | COUNT of all :FOLLOWS relationships | 110.87 | 112.22 | 223.72 |
| CognoDB Cloud | COUNT of all :FOLLOWS relationships | 587.28 | 607.31 | 650.90 |
| ArcadeDB (local Docker, capped) | COUNT of all :FOLLOWS relationships | 492.12 | 580.65 | 612.92 |
| Memgraph (local Docker, capped) | COUNT of all :FOLLOWS relationships | 16.95 | 68.82 | 75.28 |
| Neo4j 5 (local Docker, capped) | COUNT of all :FOLLOWS relationships | 1.78 | 89.37 | 91.30 |
| Memgraph Cloud | COUNT of all :FOLLOWS relationships | 292.83 | 304.88 | 382.05 |

## Mixed read/write workload (throughput ops/s, per-op p95 ms)

| Platform | 1-clients ops/s | 1-clients p95 | 10-clients ops/s | 10-clients p95 | 40-clients ops/s | 40-clients p95 |
|---|--:|--:|--:|--:|--:|--:|
| Neo4j Aura Free | 8.86 | 119.87 | 87.10 | 120.24 | 341.98 | 126.73 |
| CognoDB Cloud | 4.08 | 419.90 | 41.06 | 415.48 | 149.57 | 420.18 |
| ArcadeDB (local Docker, capped) | 483.13 | 1.73 | 870.83 | 77.69 | 1359.42 | 91.95 |
| Memgraph (local Docker, capped) | 1789.06 | 0.75 | 2543.85 | 4.26 | 2969.83 | 73.29 |
| Neo4j 5 (local Docker, capped) | 51.45 | 92.08 | 212.37 | 98.69 | 279.74 | 287.98 |
| Memgraph Cloud | 4.25 | 265.94 | 40.01 | 270.12 | 160.90 | 265.16 |

## Footprint

| Platform | Nodes stored | Rels stored | Stored size | Memory |
|---|--:|--:|---|---|
| Neo4j Aura Free | 30000 | 149890 | not observable from client (see platform console) | not observable from client (see platform console) |
| CognoDB Cloud | 30000 | 149890 | not observable from client (see platform console) | not observable from client (see platform console) |
| ArcadeDB (local Docker, capped) | 30000 | 149890 | not observable from client (see platform console) | not observable from client (see platform console) |
| Memgraph (local Docker, capped) | 30000 | 149890 | not observable from client (see platform console) | not observable from client (see platform console) |
| Neo4j 5 (local Docker, capped) | 30000 | 149890 | not observable from client (see platform console) | not observable from client (see platform console) |
| Memgraph Cloud | 30000 | 149890 | not observable from client (see platform console) | not observable from client (see platform console) |

