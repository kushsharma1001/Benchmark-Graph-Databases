package ai.wexa.gdbench.workload;

import ai.wexa.gdbench.config.BenchmarkConfig;
import ai.wexa.gdbench.dataset.DataLoader;
import ai.wexa.gdbench.db.GraphDatabaseClient;
import ai.wexa.gdbench.metrics.BenchmarkResult;
import ai.wexa.gdbench.metrics.LatencyStats;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Concurrent mixed read/write workload with a client-concurrency sweep.
 *
 * <p>For each concurrency level {@code C} (e.g. 1 / 10 / 40), {@code C} worker
 * threads hammer the database for a fixed wall-clock duration. Each op is a coin
 * flip weighted by {@code readWriteRatio}: a read (a 1-hop neighbour count) or a
 * write (create/merge a relationship between two random nodes). We report sustained
 * throughput (ops/sec) and the per-op latency distribution.
 *
 * <p>Writes {@code MERGE} a {@code TEMP} relationship type so they neither collide
 * on identical edges nor pollute the {@code FOLLOWS} graph the reads traverse. The
 * temp edges are cleaned up by the caller after the sweep.
 */
public final class MixedWorkload {

    public static final String TEMP_REL = "TEMP_BENCH_EDGE";

    private final GraphDatabaseClient client;
    private final BenchmarkConfig.Mixed cfg;
    private final int nodeCount;

    public MixedWorkload(GraphDatabaseClient client, BenchmarkConfig.Mixed cfg, int nodeCount) {
        this.client = client;
        this.cfg = cfg;
        this.nodeCount = nodeCount;
    }

    public BenchmarkResult.MixedMetrics run(int concurrency) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch go = new CountDownLatch(1);
        AtomicLong deadlineNanos = new AtomicLong();

        LongAdder reads = new LongAdder();
        LongAdder writes = new LongAdder();
        LongAdder errors = new LongAdder();
        // Per-op latencies (ms). Each worker appends to its own list; merged at end.
        List<List<Double>> perWorkerLatencies = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            perWorkerLatencies.add(new ArrayList<>());
        }

        String readCypher = "MATCH (n:" + DataLoader.NODE_LABEL + " {id:$id})"
                + "-[:" + DataLoader.REL_TYPE + "]->(m) RETURN count(m) AS c";
        String writeCypher = "MATCH (a:" + DataLoader.NODE_LABEL + " {id:$a}), "
                + "(b:" + DataLoader.NODE_LABEL + " {id:$b}) "
                + "MERGE (a)-[:" + TEMP_REL + "]->(b)";

        for (int w = 0; w < concurrency; w++) {
            final int workerId = w;
            pool.submit(() -> {
                // Distinct seed per worker so they don't all issue identical ops,
                // but still deterministic given the base seed.
                Random rnd = new Random(1000L + workerId);
                List<Double> lat = perWorkerLatencies.get(workerId);
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try (Session s = client.session()) {
                    while (System.nanoTime() < deadlineNanos.get()) {
                        boolean isRead = rnd.nextDouble() < cfg.readWriteRatio;
                        long t0 = System.nanoTime();
                        try {
                            if (isRead) {
                                Map<String, Object> p = new HashMap<>();
                                p.put("id", (long) rnd.nextInt(nodeCount));
                                s.run(readCypher, p).consume();
                                reads.increment();
                            } else {
                                Map<String, Object> p = new HashMap<>();
                                p.put("a", (long) rnd.nextInt(nodeCount));
                                p.put("b", (long) rnd.nextInt(nodeCount));
                                s.run(writeCypher, p).consume();
                                writes.increment();
                            }
                            lat.add((System.nanoTime() - t0) / 1_000_000.0);
                        } catch (RuntimeException ex) {
                            errors.increment();
                        }
                    }
                }
            });
        }

        // Start everyone at once, run for the configured duration.
        ready.await();
        long startNanos = System.nanoTime();
        deadlineNanos.set(startNanos + cfg.durationSeconds * 1_000_000_000L);
        go.countDown();

        pool.shutdown();
        // Give workers the run duration plus generous slack to drain in-flight ops.
        if (!pool.awaitTermination(cfg.durationSeconds + 60L, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1e9;

        // Merge latencies.
        int total = 0;
        for (List<Double> l : perWorkerLatencies) {
            total += l.size();
        }
        double[] all = new double[total];
        int idx = 0;
        for (List<Double> l : perWorkerLatencies) {
            for (double d : l) {
                all[idx++] = d;
            }
        }

        BenchmarkResult.MixedMetrics m = new BenchmarkResult.MixedMetrics();
        m.concurrency = concurrency;
        m.readWriteRatio = cfg.readWriteRatio;
        m.durationSeconds = cfg.durationSeconds;
        m.reads = reads.sum();
        m.writes = writes.sum();
        m.errors = errors.sum();
        m.totalOps = m.reads + m.writes;
        m.throughputOpsPerSecond = round(m.totalOps / elapsedSeconds);
        m.latency = BenchmarkResult.StatsView.from(LatencyStats.of(all));
        return m;
    }

    /** Remove the temp edges created by the write side, so re-runs start clean. */
    public void cleanup() {
        try (Session s = client.session()) {
            long deleted;
            do {
                deleted = s.executeWrite(tx -> tx.run(
                        "MATCH ()-[r:" + TEMP_REL + "]->() WITH r LIMIT 20000 "
                                + "DELETE r RETURN count(r) AS c")
                        .single().get("c").asLong());
            } while (deleted > 0);
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
