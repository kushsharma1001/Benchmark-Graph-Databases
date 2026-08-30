package ai.wexa.gdbench.dataset;

import ai.wexa.gdbench.config.BenchmarkConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Random;
import java.util.NoSuchElementException;

/**
 * A streaming source of directed edges {@code (src, dst)} plus the node-id space.
 *
 * <p>Two modes, chosen in {@code benchmark.yaml}:
 * <ul>
 *   <li><b>synthetic</b> — a deterministic scale-free-ish social graph built from a
 *       fixed seed. Deterministic matters: every platform is loaded with the byte-for-byte
 *       identical graph, and re-runs reproduce it exactly.</li>
 *   <li><b>csv</b> — a SNAP-style edge list ({@code src dst} per line, {@code #}
 *       comments ignored), e.g. a sample of soc-Pokec. Node ids are taken as-is.</li>
 * </ul>
 *
 * <p>Edges are streamed, never materialised into a big list, so the loader can push
 * hundreds of thousands of relationships without holding them all in memory.
 */
public final class EdgeStream implements Iterable<long[]> {

    private final int nodeCount;
    private final int edgeCount;
    private final long seed;
    private final Path csvFile;
    private final boolean synthetic;

    private EdgeStream(int nodeCount, int edgeCount, long seed, Path csvFile, boolean synthetic) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.seed = seed;
        this.csvFile = csvFile;
        this.synthetic = synthetic;
    }

    public static EdgeStream from(BenchmarkConfig.Dataset cfg) {
        if ("csv".equalsIgnoreCase(cfg.mode)) {
            if (cfg.edgeFile == null || cfg.edgeFile.isBlank()) {
                throw new IllegalArgumentException("dataset.mode=csv requires dataset.edgeFile");
            }
            Path f = Path.of(cfg.edgeFile);
            CsvStats stats = scanCsv(f);
            long span = stats.maxNodeId + 1;
            if (span > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("edge file node-id space too large: " + span);
            }
            return new EdgeStream((int) span, stats.edges, cfg.seed, f, false);
        }
        return new EdgeStream(cfg.nodes, cfg.relationships, cfg.seed, null, true);
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int edgeCount() {
        return edgeCount;
    }

    public String describe() {
        return synthetic
                ? "synthetic social graph (seed=" + seed + "): "
                        + nodeCount + " nodes, ~" + edgeCount + " relationships"
                : "CSV edge list " + csvFile + ": " + nodeCount + " nodes, " + edgeCount + " relationships";
    }

    @Override
    public Iterator<long[]> iterator() {
        return synthetic ? new SyntheticIterator() : new CsvIterator();
    }

    // ------------------------------------------------------------------
    // Synthetic generator: preferential-attachment-style social graph.
    // Each new node attaches to a few earlier nodes, biased toward
    // high-degree "hubs" — producing a realistic skewed degree distribution
    // (a handful of very-connected accounts, a long tail of sparse ones),
    // which is what makes multi-hop traversal timings interesting.
    // ------------------------------------------------------------------
    private final class SyntheticIterator implements Iterator<long[]> {
        private final Random rnd = new Random(seed);
        // Reservoir of already-placed endpoints, weighted by degree via repetition.
        private final int[] targets;
        private int targetSize = 0;
        private long produced = 0;
        private int nextNode = 0;
        // Pending edges from the current node's attachment step.
        private long curSrc = -1;
        private int remainingForNode = 0;

        SyntheticIterator() {
            // Cap the reservoir; degree weighting via sampling remains representative.
            targets = new int[Math.min(edgeCount * 2 + nodeCount, 4_000_000)];
            // Seed the reservoir with node 0 so the first attachment has a target.
            targets[targetSize++] = 0;
        }

        @Override
        public boolean hasNext() {
            return produced < edgeCount;
        }

        @Override
        public long[] next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (remainingForNode == 0) {
                // Advance to the next source node and decide its out-degree (1..3),
                // wrapping node ids so we keep producing edges until edgeCount is met.
                curSrc = nextNode % nodeCount;
                nextNode++;
                remainingForNode = 1 + rnd.nextInt(3);
            }
            // Pick a target biased toward high-degree nodes (sample the reservoir).
            int t = targets[rnd.nextInt(targetSize)];
            long src = curSrc;
            long dst = t;
            // Avoid trivial self loops by nudging to a neighbour id.
            if (dst == src) {
                dst = (src + 1) % nodeCount;
            }
            // Record both endpoints so their future selection probability grows
            // with degree (preferential attachment).
            if (targetSize < targets.length) {
                targets[targetSize++] = (int) src;
            }
            if (targetSize < targets.length) {
                targets[targetSize++] = (int) dst;
            }
            remainingForNode--;
            produced++;
            return new long[]{src, dst};
        }
    }

    // ------------------------------------------------------------------
    // CSV (SNAP edge list) iterator.
    // ------------------------------------------------------------------
    private final class CsvIterator implements Iterator<long[]> {
        private final BufferedReader reader;
        private long[] lookahead;

        CsvIterator() {
            try {
                this.reader = Files.newBufferedReader(csvFile);
                advance();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void advance() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split("[\\s,]+");
                    if (parts.length < 2) {
                        continue;
                    }
                    lookahead = new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
                    return;
                }
                lookahead = null;
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean hasNext() {
            return lookahead != null;
        }

        @Override
        public long[] next() {
            if (lookahead == null) {
                throw new NoSuchElementException();
            }
            long[] e = lookahead;
            advance();
            return e;
        }
    }

    private record CsvStats(long maxNodeId, int edges) {
    }

    private static CsvStats scanCsv(Path f) {
        long maxId = 0;
        int edges = 0;
        try (BufferedReader r = Files.newBufferedReader(f)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("[\\s,]+");
                if (parts.length < 2) {
                    continue;
                }
                maxId = Math.max(maxId, Math.max(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
                edges++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan edge file " + f, e);
        }
        return new CsvStats(maxId, edges);
    }
}
