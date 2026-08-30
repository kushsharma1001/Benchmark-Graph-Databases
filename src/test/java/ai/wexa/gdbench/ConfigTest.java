package ai.wexa.gdbench;

import ai.wexa.gdbench.config.BenchmarkConfig;
import ai.wexa.gdbench.config.Configs;
import ai.wexa.gdbench.config.PlatformConfig;
import ai.wexa.gdbench.dataset.EdgeStream;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void platformsYamlLoadsAndHasCognodb() {
        List<PlatformConfig> platforms = Configs.loadPlatforms(Path.of("config/platforms.yaml"));
        assertFalse(platforms.isEmpty());
        assertTrue(platforms.stream().anyMatch(p -> p.id.equals("cognodb")),
                "cognodb platform must be present");
        // At least CognoDB + 4 others, per the brief. The roster is currently 6:
        // cognodb, aura-free, memgraph-cloud, local-neo4j, local-memgraph, local-arcadedb.
        assertTrue(platforms.size() >= 5, "expected CognoDB plus >=4 comparison platforms");
        assertTrue(platforms.stream().anyMatch(p -> p.id.equals("local-arcadedb")),
                "local-arcadedb platform must be present");
        // Env var names are derived, not stored — verify the convention.
        PlatformConfig cogno = platforms.stream()
                .filter(p -> p.id.equals("cognodb")).findFirst().orElseThrow();
        assertEquals("COGNODB_URI", cogno.uriEnv());
        assertEquals("COGNODB_PASSWORD", cogno.passwordEnv());
    }

    @Test
    void benchmarkYamlLoadsWithSaneDefaults() {
        BenchmarkConfig cfg = Configs.loadBenchmark(Path.of("config/benchmark.yaml"));
        assertTrue(cfg.workload.measuredIterations >= 100,
                "brief asks for >=100 measured iterations");
        assertTrue(cfg.dataset.relationships >= 100_000,
                "brief asks for >=100k relationships");
        assertEquals(List.of(1, 2, 3), cfg.workload.hops);
    }

    @Test
    void syntheticGraphIsDeterministicAndSized() {
        BenchmarkConfig.Dataset d = new BenchmarkConfig.Dataset();
        d.mode = "synthetic";
        d.seed = 42;
        d.nodes = 5000;
        d.relationships = 20000;

        // Two independent streams with the same seed must yield identical edges.
        EdgeStream a = EdgeStream.from(d);
        EdgeStream b = EdgeStream.from(d);
        assertEquals(a.edgeCount(), b.edgeCount());

        int count = 0;
        var itA = a.iterator();
        var itB = b.iterator();
        Set<Long> seenSources = new HashSet<>();
        while (itA.hasNext() && itB.hasNext()) {
            long[] ea = itA.next();
            long[] eb = itB.next();
            assertEquals(ea[0], eb[0], "src must be deterministic");
            assertEquals(ea[1], eb[1], "dst must be deterministic");
            assertTrue(ea[0] >= 0 && ea[0] < d.nodes);
            assertTrue(ea[1] >= 0 && ea[1] < d.nodes);
            seenSources.add(ea[0]);
            count++;
        }
        assertEquals(d.relationships, count, "must produce exactly the requested edge count");
        assertTrue(seenSources.size() > 1, "graph must span many source nodes");
    }
}
