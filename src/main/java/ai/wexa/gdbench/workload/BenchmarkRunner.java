package ai.wexa.gdbench.workload;

import ai.wexa.gdbench.config.BenchmarkConfig;
import ai.wexa.gdbench.config.PlatformConfig;
import ai.wexa.gdbench.dataset.DataLoader;
import ai.wexa.gdbench.dataset.EdgeStream;
import ai.wexa.gdbench.db.GraphDatabaseClient;
import ai.wexa.gdbench.metrics.BenchmarkResult;
import ai.wexa.gdbench.metrics.LatencyStats;

/**
 * Orchestrates a full benchmark for one platform: (optionally) load the dataset,
 * then run every workload from section 5.2, populating a {@link BenchmarkResult}.
 *
 * <p>This is the single place that defines the ORDER and DISCIPLINE of a run, so
 * every platform is treated identically. It does not open connections or write
 * files — those are the CLI's job — which keeps it unit-testable.
 */
public final class BenchmarkRunner {

    private final GraphDatabaseClient client;
    private final BenchmarkConfig cfg;
    private final PlatformConfig platform;

    public BenchmarkRunner(GraphDatabaseClient client, BenchmarkConfig cfg, PlatformConfig platform) {
        this.client = client;
        this.cfg = cfg;
        this.platform = platform;
    }

    /** Load the dataset and record ingest metrics into {@code result}. */
    public void load(BenchmarkResult result, boolean wipeFirst) {
        EdgeStream edges = EdgeStream.from(cfg.dataset);
        result.datasetDescription = edges.describe();

        DataLoader loader = new DataLoader(client, cfg.load);
        if (wipeFirst) {
            System.out.println("  wiping existing graph...");
            loader.wipe();
        }
        System.out.println("  loading " + edges.describe());
        DataLoader.LoadResult lr = loader.load(edges);

        result.load.nodes = lr.nodesLoaded();
        result.load.relationships = lr.relationshipsLoaded();
        result.load.wallClockSeconds = lr.wallClockSeconds();
        result.load.nodesPerSecond = lr.nodesPerSecond();
        result.load.relationshipsPerSecond = lr.relationshipsPerSecond();
        System.out.printf("  loaded %d nodes, %d rels in %.2fs (%.0f nodes/s, %.0f rels/s)%n",
                lr.nodesLoaded(), lr.relationshipsLoaded(), lr.wallClockSeconds(),
                lr.nodesPerSecond(), lr.relationshipsPerSecond());
    }

    /** Run all read + mixed workloads, filling {@code result}. Assumes data is loaded. */
    public void benchmark(BenchmarkResult result) throws InterruptedException {
        int nodeCount = nodeCountForWorkloads(result);

        ReadWorkloads reads = new ReadWorkloads(client, cfg.workload, nodeCount);

        // Traversals: 1/2/3-hop.
        for (int hops : cfg.workload.hops) {
            System.out.println("  traversal " + hops + "-hop...");
            LatencyStats s = reads.traversal(hops);
            result.traversal.put(hops + "-hop", BenchmarkResult.StatsView.from(s));
        }

        // Lookups.
        System.out.println("  point lookup...");
        result.pointLookup = BenchmarkResult.StatsView.from(reads.pointLookup());
        System.out.println("  indexed/filtered lookup...");
        result.indexedLookup = BenchmarkResult.StatsView.from(reads.indexedLookup());
        result.indexedProperties = DataLoader.NODE_LABEL + ".id (range index)";

        // Aggregation.
        System.out.println("  aggregation (count relationships)...");
        result.aggregation = BenchmarkResult.StatsView.from(reads.aggregation());
        result.aggregationDescription =
                "COUNT of all :" + DataLoader.REL_TYPE + " relationships";

        // Mixed workload concurrency sweep.
        MixedWorkload mixed = new MixedWorkload(client, cfg.mixed, nodeCount);
        for (int c : cfg.mixed.concurrencyLevels) {
            System.out.println("  mixed workload @ " + c + " clients for "
                    + cfg.mixed.durationSeconds + "s...");
            BenchmarkResult.MixedMetrics m = mixed.run(c);
            result.mixed.put(c + "-clients", m);
            System.out.printf("    %.0f ops/s (reads=%d writes=%d errors=%d)%n",
                    m.throughputOpsPerSecond, m.reads, m.writes, m.errors);
        }
        mixed.cleanup();

        // Footprint — record what we can observe from the client side.
        captureFootprint(result);
    }

    /**
     * The workload RNG must draw start nodes from the same id space that was loaded.
     * We trust the configured dataset size, but if a prior load recorded a different
     * node count we honour that (e.g. a CSV dataset).
     */
    private int nodeCountForWorkloads(BenchmarkResult result) {
        if (result.load != null && result.load.nodes > 0) {
            return result.load.nodes;
        }
        return EdgeStream.from(cfg.dataset).nodeCount();
    }

    private void captureFootprint(BenchmarkResult result) {
        // Stored sizes/memory are only observable on some engines. We try the
        // portable count and note the rest as not-observable-from-client.
        try (var s = client.session()) {
            long nodes = s.run("MATCH (n) RETURN count(n) AS c").single().get("c").asLong();
            long rels = s.run("MATCH ()-[r]->() RETURN count(r) AS c").single().get("c").asLong();
            result.footprint.put("nodesStored", Long.toString(nodes));
            result.footprint.put("relationshipsStored", Long.toString(rels));
        } catch (RuntimeException e) {
            result.caveats.put("footprint", "count query failed: " + e.getMessage());
        }
        result.footprint.put("storedDataSize",
                "not observable from client (see platform console)");
        result.footprint.put("memoryUsage",
                "not observable from client (see platform console)");
    }
}
