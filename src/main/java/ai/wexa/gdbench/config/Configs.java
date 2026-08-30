package ai.wexa.gdbench.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Loads the YAML config files and resolves per-platform credentials from the environment. */
public final class Configs {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private Configs() {
    }

    public static List<PlatformConfig> loadPlatforms(Path file) {
        try {
            PlatformConfig.Registry reg =
                    YAML.readValue(Files.readAllBytes(file), PlatformConfig.Registry.class);
            if (reg.platforms == null || reg.platforms.isEmpty()) {
                throw new IllegalStateException("No platforms defined in " + file);
            }
            return reg.platforms;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read platforms file: " + file, e);
        }
    }

    public static BenchmarkConfig loadBenchmark(Path file) {
        try {
            return YAML.readValue(Files.readAllBytes(file), BenchmarkConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read benchmark config: " + file, e);
        }
    }

    /**
     * Resolve credentials for a platform from the environment. Returns empty when
     * the URI or password is not set, which the caller treats as "skip this
     * platform" rather than an error — so a partial credential set still runs.
     */
    public static Optional<Credentials> credentials(PlatformConfig p) {
        String uri = env(p.uriEnv());
        String user = env(p.userEnv());
        String password = env(p.passwordEnv());
        if (uri == null || uri.isBlank()) {
            return Optional.empty();
        }
        // Some local engines (Memgraph) accept empty auth; only the URI is required.
        return Optional.of(new Credentials(uri, user == null ? "" : user,
                password == null ? "" : password));
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return v == null ? System.getProperty(key) : v;
    }

    /** Immutable connection credentials, sourced only from the environment. */
    public record Credentials(String uri, String user, String password) {
    }
}
