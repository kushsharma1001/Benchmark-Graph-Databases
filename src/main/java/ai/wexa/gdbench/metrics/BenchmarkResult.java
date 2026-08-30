package ai.wexa.gdbench.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The full result set for ONE platform: everything section 5.2 of the brief asks
 * for. Serialized to {@code results/<platform>.json} and rendered into the README
 * results matrix. All fields are plain public data so Jackson serializes directly.
 */
public final class BenchmarkResult {

    public String platformId;
    public String platformDisplayName;
    public String tier;
    public String engine;
    public String serverInfo;
    public Map<String, String> specs = new LinkedHashMap<>();

    /** ISO-8601 UTC timestamp of the run, supplied by the caller (no wall-clock in this class). */
    public String runTimestampUtc;
    public String clientEnvironment;   // JVM, OS, region note supplied by caller
    public String datasetDescription;

    // --- Data loading (5.2: ingest throughput) ---
    public LoadMetrics load = new LoadMetrics();

    // --- Traversals (1/2/3-hop) : hop -> stats ---
    public Map<String, StatsView> traversal = new LinkedHashMap<>();

    // --- Lookups ---
    public StatsView pointLookup;
    public StatsView indexedLookup;
    public String indexedProperties;   // which properties are indexed on this platform

    // --- Aggregation ---
    public StatsView aggregation;
    public String aggregationDescription;

    // --- Mixed workload : concurrency level -> throughput ---
    public Map<String, MixedMetrics> mixed = new LinkedHashMap<>();

    // --- Footprint (5.2) ---
    public Map<String, String> footprint = new LinkedHashMap<>();

    // --- Honest record of anything that failed / was skipped ---
    public Map<String, String> caveats = new LinkedHashMap<>();

    public static final class LoadMetrics {
        public int nodes;
        public int relationships;
        public double wallClockSeconds;
        public double nodesPerSecond;
        public double relationshipsPerSecond;
    }

    public static final class MixedMetrics {
        public int concurrency;
        public double readWriteRatio;
        public int durationSeconds;
        public long totalOps;
        public long reads;
        public long writes;
        public long errors;
        public double throughputOpsPerSecond;
        public StatsView latency;   // per-op latency across all workers
    }

    /** Flattened view of {@link LatencyStats} for JSON output. */
    public static final class StatsView {
        public int count;
        public double min;
        public double mean;
        public double p50;
        public double p95;
        public double p99;
        public double max;
        public double stddev;

        public static StatsView from(LatencyStats s) {
            StatsView v = new StatsView();
            v.count = s.count();
            v.min = s.min();
            v.mean = s.mean();
            v.p50 = s.p50();
            v.p95 = s.p95();
            v.p99 = s.p99();
            v.max = s.max();
            v.stddev = s.stddev();
            return v;
        }
    }
}
