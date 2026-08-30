package ai.wexa.gdbench.report;

import ai.wexa.gdbench.metrics.BenchmarkResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads/writes per-platform {@link BenchmarkResult} JSON under {@code results/}. */
public final class ResultStore {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path dir;

    public ResultStore(Path dir) {
        this.dir = dir;
    }

    public Path save(BenchmarkResult result) {
        try {
            Files.createDirectories(dir);
            Path out = dir.resolve(result.platformId + ".json");
            JSON.writeValue(out.toFile(), result);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write result for " + result.platformId, e);
        }
    }

    /**
     * Delete this run's prior output so the directory holds only fresh results:
     * every top-level {@code *.json} plus the generated {@code RESULTS.md} and
     * {@code results.csv}. Subdirectories (e.g. {@code raw/}) are left untouched.
     */
    public void clear() {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".json") || n.equals("RESULTS.md") || n.equals("results.csv");
                    })
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Failed to delete " + p, e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clear results in " + dir, e);
        }
    }

    public BenchmarkResult load(Path file) {
        try {
            return JSON.readValue(Files.readAllBytes(file), BenchmarkResult.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read result file " + file, e);
        }
    }

    /** All {@code *.json} results in the directory (excludes any raw/ subdir). */
    public List<BenchmarkResult> loadAll() {
        List<BenchmarkResult> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(p -> out.add(load(p)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list results in " + dir, e);
        }
        return out;
    }
}
