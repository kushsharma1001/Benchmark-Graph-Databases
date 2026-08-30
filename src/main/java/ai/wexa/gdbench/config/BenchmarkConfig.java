package ai.wexa.gdbench.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Parsed {@code config/benchmark.yaml}: the identical, platform-independent
 * settings applied to every database so the comparison stays fair.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BenchmarkConfig {

    public Dataset dataset = new Dataset();
    public Load load = new Load();
    public Workload workload = new Workload();
    public Mixed mixed = new Mixed();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Dataset {
        public String mode = "synthetic";
        public long seed = 42;
        public int nodes = 30000;
        public int relationships = 150000;
        public String edgeFile = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Load {
        public int batchSize = 10000;
        public boolean createIndexes = true;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Workload {
        public int warmupIterations = 30;
        public int measuredIterations = 120;
        public long randomSeed = 7;
        public List<Integer> hops = List.of(1, 2, 3);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Mixed {
        public List<Integer> concurrencyLevels = List.of(1, 10, 40);
        public int durationSeconds = 20;
        public double readWriteRatio = 0.9;
    }
}
