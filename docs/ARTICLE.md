# How I Benchmarked Six Graph Databases
*Why the tail latency — and the network cable — are the only numbers that tell the truth.*

---

I distrust the "we benchmarked X vs Y and X is 10× faster!" genre. Usually X had more RAM,
or the data fit X's cache but not Y's, or X ran warm and Y cold.

So when I compared **CognoDB Cloud** against five other graph databases — three managed
clouds and three resource-capped local engines.

## Rule zero: same everything

Four rules, no exceptions: **same dataset** (identical nodes, edges, order), **same queries**
(byte-for-byte), **same resources** (free tier vs free tier), **same discipline** (warm up,
same iteration count, same percentiles).

The first two gave me a decision for free. CognoDB, Neo4j Aura, Memgraph, and even ArcadeDB
all speak the **Bolt protocol** and understand **Cypher**, so I point *one* driver — the
official Neo4j Java driver — at all of them and run the *exact same query text*. No
per-vendor client. Only the connection string changes. That kills the most common confound —
"different client overhead" — before I write a line of workload code.

## Rule 3 is where honesty gets uncomfortable

CognoDB's free tier is tiny: **0.5 vCPU, 256 MB, 1 GB.** The fair move is to cap every
competitor to that envelope, so I cap the local controls with Docker `--cpus=0.5
--memory=256m`. And that's my **first finding — before measuring a single query:**

> Stock Neo4j 5 won't boot in 256 MB. The JVM and admin tool get OOM-killed by the cgroup
> limit before the database is ready. I bumped it to 1 GB — it opened Bolt, then got
> OOM-killed again about two minutes later. Only **2 GB** is stable.

I could have quietly bumped the RAM and said nothing. Instead I ran Neo4j (and ArcadeDB) at
2 GB and wrote it in bold, because the failure *is a result*: Memgraph (C++) and CognoDB
serve queries happily at 256 MB, and a managed engine doing that is real engineering a stock
JVM container can't match. Hiding the asymmetry would have thrown away the most interesting
thing I learned.

## The caveat that governs every cloud number

I ran the whole thing from a GitHub Codespace. The three local engines answered over
`localhost`; the three clouds answered over the public internet. So **every cloud latency
includes ~100–300 ms of network round-trip**, and it dominates:

| 1-hop traversal p50 | value |
|---|--:|
| CognoDB Cloud | 207 ms |
| Neo4j Aura | 112 ms |
| Memgraph Cloud | 265 ms |
| Neo4j (local) | 3.3 ms |
| Memgraph (local) | **0.5 ms** |

Those cloud numbers aren't how fast the engines traverse a graph — they're how far away the
server is. The dead giveaway is **Memgraph Cloud: 264 ms on 1-hop, 265 ms on point lookup,
265 ms on indexed lookup.** An in-memory engine does not take the same time for three
different query shapes — that flat line is pure RTT. So the only fair comparisons are
**cloud-to-cloud** and **local-to-local**. Comparing a cloud row to a `localhost` row
measures the length of a network cable.

## Why averages lie and percentiles don't

A point lookup on the capped 0.5-vCPU Neo4j: **p50 1.94 ms, p95 90 ms.** The average would
land around 10 ms and I'd call the database "a bit slow" — a lie by omission. The truth is
bimodal: half the queries finish under 2 ms, and a tail take ~45× longer. Why? Half a core.
When the scheduler is busy the query waits — not because the engine is slow, but because
there's no core free. The p50 says the engine is fast; the p95 says the *tier* is
constrained. You need both, which is why I run ≥120 measured iterations after a warm-up —
you can't see a tail with ten samples.

## One query set, two engines, opposite answers

This is where the single-driver discipline earned its keep. Same five Cypher queries,
byte-identical, on the two most different local engines:

| Workload (p50) | Neo4j 5 | Memgraph | ArcadeDB |
|---|--:|--:|--:|
| Load 150k rels | 116 s | **1.6 s** | 46 s |
| 1-hop traversal | 3.3 ms | **0.5 ms** | 1.4 ms |
| Indexed range scan | 3.6 ms | **0.6 ms** | 87 ms |
| `COUNT` aggregation | **1.8 ms** | 17 ms | 492 ms |
| Mixed @ 40 clients | 280 ops/s | **2970 ops/s** | 1359 ops/s |

Nobody wins across the board. **Memgraph** (in-memory) dominates reads and throughput — and
does it in 256 MB. **Neo4j** crushes `COUNT` because its planner lowers a full relationship
count to a native aggregate. **ArcadeDB** is sub-millisecond on point/1-hop reads but
280× slower on `COUNT`, because its Cypher is a translation layer, not a native planner.

They're fast at *different things* — and I can only say that with a straight face because
the query text was byte-identical on all three. If I'd hand-tuned per engine or used each
vendor's client, this table would be an artifact of my choices, not a property of the
databases. The single-driver rule is what makes a surprising result *trustworthy* instead of
suspicious. (Bonus honest footnotes: ArcadeDB's Bolt endpoint is off by default, and its
image ships no `bash` so my TCP healthcheck falsely read "unhealthy" while Bolt served fine.
Both went straight into the caveats.)

## The saturation curve: where the tier actually lives

The mixed workload (90% reads, 10% writes) on local Memgraph, swept across clients:

| clients | throughput |
|--:|--:|
| 1 | 1789 ops/s |
| 10 | 2544 ops/s |
| 40 | 2970 ops/s |

From 1 to 10 clients throughput climbs ~42%; from 10 to 40 — 4× the load — it climbs ~17%
while the p95 stretches from 4 ms to 73 ms. That flattening is the sound of a 0.5-vCPU
server saturating: past a point, extra clients don't get served faster, they get *queued*,
and the queue is what the p95 measures. One number says "2970 ops/s." The sweep says "don't
bother sending more than ~10 concurrent clients at this tier" — which is the answer capacity
planning actually needs.

## What makes it reproducible

The whole thing runs with one command — `./scripts/run.sh all` — which loads the data, runs
every workload against every platform whose credentials are in the environment, writes a JSON
file per platform, and renders a Markdown matrix plus a CSV. Credentials come *only* from
environment variables; there's not a single connection string in the repo. A platform with
no credentials is skipped and *reported as skipped* — I never invent a number for an account
I don't have. All six platforms ran end-to-end with zero errors.

## The takeaway

Benchmarking is hard because the *honesty* is hard — it's always tempting to drop the failed
run, report the flattering average, or quietly hand one database more RAM. The engineering
that matters is what makes it impossible to fool yourself: identical queries via one driver,
deterministic data, seeded inputs, percentiles over hundreds of iterations, a caveats section
you're slightly embarrassed to write, and the discipline to admit that a Codespace-to-cloud
number is measuring a network cable. Do that, and even a benchmark where nobody wins teaches
you something real — like the fact that 256 MB is a wall for one engine and a business model
for another.
