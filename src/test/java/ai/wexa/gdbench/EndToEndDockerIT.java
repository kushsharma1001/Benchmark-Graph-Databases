package ai.wexa.gdbench;

import ai.wexa.gdbench.config.BenchmarkConfig;
import ai.wexa.gdbench.config.Configs;
import ai.wexa.gdbench.config.PlatformConfig;
import ai.wexa.gdbench.db.GraphDatabaseClient;
import ai.wexa.gdbench.metrics.BenchmarkResult;
import ai.wexa.gdbench.workload.BenchmarkRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof that the harness genuinely loads data and runs every workload
 * against a real Bolt server. Uses a small dataset so it finishes quickly in CI;
 * the production run uses the full {@code config/benchmark.yaml} sizes.
 *
 * <p>Tagged {@code docker} so it only runs with {@code -DDOCKER_IT=true} (needs a
 * Docker daemon). This is what lets us claim the code path is validated without
 * fabricating any cloud numbers.
 */
@Tag("docker")
@Testcontainers
class EndToEndDockerIT {

    @Container
    static final Neo4jContainer<?> NEO4J =
            new Neo4jContainer<>("neo4j:5.26-community").withAdminPassword("testpassword");

    @Test
    void loadsAndBenchmarksAgainstRealNeo4j() throws Exception {
        PlatformConfig platform = new PlatformConfig();
        platform.id = "local-neo4j-it";
        platform.displayName = "Neo4j (Testcontainers)";
        platform.tier = "container";
        platform.engine = "Bolt / Cypher";

        Configs.Credentials creds =
                new Configs.Credentials(NEO4J.getBoltUrl(), "neo4j", "testpassword");

        // Small, fast dataset for the test.
        BenchmarkConfig cfg = new BenchmarkConfig();
        cfg.dataset.mode = "synthetic";
        cfg.dataset.nodes = 2000;
        cfg.dataset.relationships = 8000;
        cfg.load.batchSize = 2000;
        cfg.workload.warmupIterations = 3;
        cfg.workload.measuredIterations = 20;
        cfg.workload.hops = List.of(1, 2);
        cfg.mixed.concurrencyLevels = List.of(1, 4);
        cfg.mixed.durationSeconds = 2;

        try (GraphDatabaseClient client = GraphDatabaseClient.connect(platform, creds)) {
            BenchmarkResult r = new BenchmarkResult();
            r.platformId = platform.id;
            r.platformDisplayName = platform.displayName;

            BenchmarkRunner runner = new BenchmarkRunner(client, cfg, platform);
            runner.load(r, true);
            runner.benchmark(r);

            // Loading produced the expected shape.
            assertEquals(2000, r.load.nodes);
            assertEquals(8000, r.load.relationships);
            assertTrue(r.load.relationshipsPerSecond > 0);

            // Every required metric was measured.
            assertNotNull(r.traversal.get("1-hop"));
            assertNotNull(r.traversal.get("2-hop"));
            assertTrue(r.traversal.get("1-hop").count == 20, "measured iterations recorded");
            assertNotNull(r.pointLookup);
            assertNotNull(r.indexedLookup);
            assertNotNull(r.aggregation);
            assertTrue(r.aggregation.p50 >= 0);

            // Mixed workload produced throughput at each concurrency level.
            assertNotNull(r.mixed.get("1-clients"));
            assertNotNull(r.mixed.get("4-clients"));
            assertTrue(r.mixed.get("4-clients").totalOps > 0, "mixed workload ran ops");

            // Footprint counts match what we loaded.
            assertEquals("2000", r.footprint.get("nodesStored"));
        }
    }
}
