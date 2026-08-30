package ai.wexa.gdbench.dataset;

import ai.wexa.gdbench.config.BenchmarkConfig;
import ai.wexa.gdbench.db.GraphDatabaseClient;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the dataset into a target database and measures ingest throughput.
 *
 * <p>Load strategy — identical on every platform for fairness:
 * <ol>
 *   <li>Optionally wipe the graph.</li>
 *   <li>Create the {@code :Person(id)} index/constraint <em>before</em> loading,
 *       so the {@code MATCH}-based edge creation is index-backed (otherwise edge
 *       loading degrades to O(n) scans per lookup — a classic unfair-load trap).</li>
 *   <li>Batch nodes, then batch edges, via {@code UNWIND $rows}. Driver-side
 *       batching is the portable common denominator: bulk-import tools differ per
 *       platform, but {@code UNWIND} runs the same everywhere.</li>
 * </ol>
 *
 * <p>Reported: total wall-clock, nodes/sec and relationships/sec.
 */
public final class DataLoader {

    /** Model of the graph we build: nodes are :Person{id}, edges are (:Person)-[:FOLLOWS]->(:Person). */
    public static final String NODE_LABEL = "Person";
    public static final String REL_TYPE = "FOLLOWS";

    private final GraphDatabaseClient client;
    private final BenchmarkConfig.Load loadCfg;

    public DataLoader(GraphDatabaseClient client, BenchmarkConfig.Load loadCfg) {
        this.client = client;
        this.loadCfg = loadCfg;
    }

    public record LoadResult(
            int nodesLoaded,
            int relationshipsLoaded,
            double wallClockSeconds,
            double nodesPerSecond,
            double relationshipsPerSecond) {
    }

    public void wipe() {
        try (Session s = client.session()) {
            // Delete in bounded batches so a tiny-RAM instance does not OOM on a
            // single giant DETACH DELETE transaction.
            long deleted;
            do {
                deleted = s.executeWrite(tx -> tx.run(
                        "MATCH (n) WITH n LIMIT 20000 DETACH DELETE n RETURN count(n) AS c")
                        .single().get("c").asLong());
            } while (deleted > 0);
        }
    }

    private void createIndexes() {
        try (Session s = client.session()) {
            // Range/lookup index on the id we MATCH by. `IF NOT EXISTS` keeps it
            // idempotent. The Neo4j-style DDL is also accepted by Neo4j Aura and,
            // verified empirically, by ArcadeDB's Bolt/Cypher layer — so only
            // Memgraph needs the fallback form below.
            try {
                s.run("CREATE INDEX person_id IF NOT EXISTS FOR (p:" + NODE_LABEL + ") ON (p.id)")
                        .consume();
            } catch (RuntimeException neo4jStyleFailed) {
                // Memgraph uses a different DDL: CREATE INDEX ON :Label(prop);
                s.run("CREATE INDEX ON :" + NODE_LABEL + "(id)").consume();
            }
        }
    }

    public LoadResult load(EdgeStream edges) {
        if (loadCfg.createIndexes) {
            createIndexes();
        }

        long start = System.nanoTime();

        // 1) Nodes. We know the id space up front (0..nodeCount-1 for synthetic;
        //    maxId+1 for CSV), so we can create them densely and deterministically.
        int nodesLoaded = loadNodes(edges.nodeCount());

        // 2) Edges, streamed in batches.
        int relsLoaded = loadEdges(edges);

        double seconds = (System.nanoTime() - start) / 1e9;
        return new LoadResult(
                nodesLoaded,
                relsLoaded,
                round(seconds),
                round(nodesLoaded / seconds),
                round(relsLoaded / seconds));
    }

    private int loadNodes(int nodeCount) {
        final String cypher =
                "UNWIND $rows AS id MERGE (p:" + NODE_LABEL + " {id: id})";
        int loaded = 0;
        List<Long> batch = new ArrayList<>(loadCfg.batchSize);
        for (int id = 0; id < nodeCount; id++) {
            batch.add((long) id);
            if (batch.size() >= loadCfg.batchSize) {
                loaded += flushNodes(cypher, batch);
                batch = new ArrayList<>(loadCfg.batchSize);
            }
        }
        if (!batch.isEmpty()) {
            loaded += flushNodes(cypher, batch);
        }
        return loaded;
    }

    private int flushNodes(String cypher, List<Long> batch) {
        try (Session s = client.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("rows", batch);
            s.executeWrite(tx -> tx.run(cypher, params).consume());
        }
        return batch.size();
    }

    private int loadEdges(EdgeStream edges) {
        final String cypher =
                "UNWIND $rows AS row "
                        + "MATCH (a:" + NODE_LABEL + " {id: row.s}) "
                        + "MATCH (b:" + NODE_LABEL + " {id: row.d}) "
                        + "MERGE (a)-[:" + REL_TYPE + "]->(b)";
        int loaded = 0;
        List<Map<String, Object>> batch = new ArrayList<>(loadCfg.batchSize);
        for (long[] e : edges) {
            Map<String, Object> row = new HashMap<>(2);
            row.put("s", e[0]);
            row.put("d", e[1]);
            batch.add(row);
            if (batch.size() >= loadCfg.batchSize) {
                loaded += flushEdges(cypher, batch);
                batch = new ArrayList<>(loadCfg.batchSize);
            }
        }
        if (!batch.isEmpty()) {
            loaded += flushEdges(cypher, batch);
        }
        return loaded;
    }

    private int flushEdges(String cypher, List<Map<String, Object>> batch) {
        try (Session s = client.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("rows", batch);
            s.executeWrite(tx -> tx.run(cypher, params).consume());
        }
        return batch.size();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
