package ai.wexa.gdbench.workload;

import ai.wexa.gdbench.config.BenchmarkConfig;
import ai.wexa.gdbench.dataset.DataLoader;
import ai.wexa.gdbench.db.GraphDatabaseClient;
import ai.wexa.gdbench.metrics.LatencyStats;
import ai.wexa.gdbench.metrics.Timer;
import org.neo4j.driver.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Single-threaded read workloads: traversals, lookups and aggregation.
 *
 * <p>Every workload follows the same discipline the brief requires:
 * <ol>
 *   <li><b>Warm-up</b> — {@code warmupIterations} untimed calls so the server's
 *       page cache / query plan cache is hot before measurement.</li>
 *   <li><b>Measure</b> — {@code measuredIterations} (&ge; 100) timed calls.</li>
 *   <li><b>Same inputs everywhere</b> — start nodes are drawn from a fixed-seed RNG,
 *       so every platform is asked the identical set of questions.</li>
 * </ol>
 *
 * <p>The queries are plain Cypher that runs unchanged on Neo4j, CognoDB and
 * Memgraph. Each returns an aggregate (a count), so the whole result set does not
 * have to be streamed to the client — we measure the database's traversal cost,
 * not network transfer of a large payload.
 */
public final class ReadWorkloads {

    private final GraphDatabaseClient client;
    private final BenchmarkConfig.Workload cfg;
    private final int nodeCount;

    public ReadWorkloads(GraphDatabaseClient client, BenchmarkConfig.Workload cfg, int nodeCount) {
        this.client = client;
        this.cfg = cfg;
        this.nodeCount = nodeCount;
    }

    /**
     * n-hop traversal latency from random start nodes. Counts the distinct nodes
     * reachable in exactly-up-to {@code hops} steps — an expanding neighbourhood,
     * the classic graph traversal whose cost grows with hop depth.
     */
    public LatencyStats traversal(int hops) {
        String rel = "-[:" + DataLoader.REL_TYPE + "]->";
        StringBuilder pattern = new StringBuilder("(n:" + DataLoader.NODE_LABEL + " {id:$id})");
        for (int i = 0; i < hops; i++) {
            pattern.append(rel).append("(x").append(i).append(")");
        }
        // COUNT DISTINCT the final frontier node — variable-length would also work,
        // but an explicit fixed pattern keeps the plan identical across engines.
        String cypher = "MATCH " + pattern + " RETURN count(DISTINCT x" + (hops - 1) + ") AS c";
        return runTimed(cypher, /*randomStart*/ true);
    }

    /**
     * Point lookup by primary id. With the id index present this is the
     * index-backed fast path; we report it separately from a filtered scan below.
     */
    public LatencyStats pointLookup() {
        String cypher = "MATCH (n:" + DataLoader.NODE_LABEL + " {id:$id}) RETURN n.id AS id";
        return runTimed(cypher, true);
    }

    /**
     * Indexed / filtered lookup: a range predicate over the indexed id property.
     * Contrasts with the point lookup to show index selectivity behaviour. The
     * band width is fixed so the amount of work is identical on every platform.
     */
    public LatencyStats indexedLookup() {
        String cypher = "MATCH (n:" + DataLoader.NODE_LABEL + ") "
                + "WHERE n.id >= $id AND n.id < $id + 100 RETURN count(n) AS c";
        return runTimed(cypher, true);
    }

    /**
     * Aggregation over a relationship type: total degree grouped — a count/group-by
     * style query as required by 5.2. Runs the same aggregate each iteration (no
     * random parameter) because it scans the whole relationship set.
     */
    public LatencyStats aggregation() {
        String cypher = "MATCH (:" + DataLoader.NODE_LABEL + ")-[r:" + DataLoader.REL_TYPE + "]->() "
                + "RETURN count(r) AS totalRels";
        return runTimed(cypher, false);
    }

    // ------------------------------------------------------------------
    // Shared warm-up + measure loop.
    // ------------------------------------------------------------------
    private LatencyStats runTimed(String cypher, boolean randomStart) {
        Random rnd = new Random(cfg.randomSeed);

        // Warm-up (untimed).
        try (Session s = client.session()) {
            for (int i = 0; i < cfg.warmupIterations; i++) {
                s.run(cypher, params(randomStart, rnd)).consume();
            }
        }

        // Measure. Reset RNG so the measured start-node sequence is deterministic
        // and identical across platforms.
        rnd = new Random(cfg.randomSeed);
        Timer timer = new Timer(cfg.measuredIterations);
        try (Session s = client.session()) {
            for (int i = 0; i < cfg.measuredIterations; i++) {
                Map<String, Object> p = params(randomStart, rnd);
                timer.time(() -> s.run(cypher, p).consume());
            }
        }
        return timer.stats();
    }

    private Map<String, Object> params(boolean randomStart, Random rnd) {
        Map<String, Object> p = new HashMap<>();
        if (randomStart) {
            p.put("id", (long) rnd.nextInt(nodeCount));
        }
        return p;
    }
}
