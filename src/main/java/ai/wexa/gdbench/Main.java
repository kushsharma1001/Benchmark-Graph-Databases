package ai.wexa.gdbench;

import ai.wexa.gdbench.config.BenchmarkConfig;
import ai.wexa.gdbench.config.Configs;
import ai.wexa.gdbench.config.PlatformConfig;
import ai.wexa.gdbench.db.GraphDatabaseClient;
import ai.wexa.gdbench.metrics.BenchmarkResult;
import ai.wexa.gdbench.report.ReportGenerator;
import ai.wexa.gdbench.report.ResultStore;
import ai.wexa.gdbench.workload.BenchmarkRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * CLI entry point. One binary, four subcommands:
 *
 * <pre>
 *   gdbench load    --platform &lt;id|all&gt;   # load the dataset into a platform
 *   gdbench bench   --platform &lt;id|all&gt;   # run the workloads (assumes data loaded)
 *   gdbench all     --platform &lt;id|all&gt;   # load + bench + write JSON  (one command)
 *   gdbench report                        # render RESULTS.md + results.csv from JSON
 * </pre>
 *
 * <p>A platform is only touched if its credentials are present in the environment,
 * so {@code --platform all} runs whatever subset you have accounts for. Nothing is
 * fabricated: a platform with no credentials is skipped and reported as such.
 */
@Command(name = "gdbench",
        mixinStandardHelpOptions = true,
        version = "cognodb-graph-benchmark 1.0.0",
        description = "Fair, reproducible graph-database cloud benchmark (Bolt/Cypher).",
        subcommands = {
                Main.LoadCmd.class,
                Main.BenchCmd.class,
                Main.AllCmd.class,
                Main.ReportCmd.class
        })
public final class Main implements Callable<Integer> {

    @Override
    public Integer call() {
        // No subcommand: show help.
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        // Optionally load config/application.properties (or -Dgdbench.properties=...),
        // resolving ${NAME:default} placeholders into system properties. Real env
        // vars still win, so this is purely an additive convenience over .env.
        ai.wexa.gdbench.config.PropertiesSupport.loadIfPresent();
        int code = new CommandLine(new Main()).execute(args);
        System.exit(code);
    }

    // ------------------------------------------------------------------
    // Shared options for the platform-touching commands.
    // ------------------------------------------------------------------
    abstract static class PlatformCommand implements Callable<Integer> {
        @Option(names = {"-p", "--platform"}, defaultValue = "all",
                description = "Platform id from platforms.yaml, or 'all' (default).")
        String platform;

        @Option(names = "--platforms-file", defaultValue = "config/platforms.yaml",
                description = "Path to the platform registry YAML.")
        Path platformsFile;

        @Option(names = "--benchmark-file", defaultValue = "config/benchmark.yaml",
                description = "Path to the benchmark parameters YAML.")
        Path benchmarkFile;

        @Option(names = "--results-dir", defaultValue = "results",
                description = "Directory for per-platform JSON output.")
        Path resultsDir;

        List<PlatformConfig> selectedPlatforms() {
            List<PlatformConfig> all = Configs.loadPlatforms(platformsFile);
            if ("all".equalsIgnoreCase(platform)) {
                return all;
            }
            return all.stream()
                    .filter(p -> p.id.equalsIgnoreCase(platform))
                    .toList();
        }

        /** Build a fresh result skeleton with metadata filled from config + env. */
        BenchmarkResult freshResult(PlatformConfig p) {
            BenchmarkResult r = new BenchmarkResult();
            r.platformId = p.id;
            r.platformDisplayName = p.displayName;
            r.tier = p.tier;
            r.engine = p.engine;
            if (p.specs != null) {
                r.specs.putAll(p.specs);
            }
            r.runTimestampUtc = Instant.now().toString();
            r.clientEnvironment = "JVM " + System.getProperty("java.version")
                    + " on " + System.getProperty("os.name")
                    + " / " + System.getProperty("os.arch");
            return r;
        }
    }

    @Command(name = "load", description = "Load the dataset into the platform(s).")
    static final class LoadCmd extends PlatformCommand {
        @Option(names = "--no-wipe", description = "Do not wipe existing data before loading.")
        boolean noWipe;

        @Override
        public Integer call() {
            BenchmarkConfig cfg = Configs.loadBenchmark(benchmarkFile);
            ResultStore store = new ResultStore(resultsDir);
            int touched = forEachPlatform(selectedPlatforms(), (p, client) -> {
                BenchmarkResult r = freshResult(p);
                r.serverInfo = client.serverInfo();
                new BenchmarkRunner(client, cfg, p).load(r, !noWipe);
                store.save(r);
            });
            return touched == 0 ? noneRan() : 0;
        }
    }

    @Command(name = "bench", description = "Run workloads on already-loaded platform(s).")
    static final class BenchCmd extends PlatformCommand {
        @Override
        public Integer call() {
            BenchmarkConfig cfg = Configs.loadBenchmark(benchmarkFile);
            ResultStore store = new ResultStore(resultsDir);
            int touched = forEachPlatform(selectedPlatforms(), (p, client) -> {
                // Reuse the load metrics already on disk if present, else fresh.
                BenchmarkResult r = existingOrFresh(store, resultsDir, p);
                r.serverInfo = client.serverInfo();
                new BenchmarkRunner(client, cfg, p).benchmark(r);
                store.save(r);
            });
            return touched == 0 ? noneRan() : 0;
        }
    }

    @Command(name = "all", description = "Load + benchmark + save JSON in one command.")
    static final class AllCmd extends PlatformCommand {
        @Option(names = "--no-wipe", description = "Do not wipe existing data before loading.")
        boolean noWipe;

        @Override
        public Integer call() {
            BenchmarkConfig cfg = Configs.loadBenchmark(benchmarkFile);
            ResultStore store = new ResultStore(resultsDir);
            // The results dir always reflects ONLY the current run: clear stale
            // output first so RESULTS.md never merges last run's leftovers.
            store.clear();
            int touched = forEachPlatform(selectedPlatforms(), (p, client) -> {
                BenchmarkResult r = freshResult(p);
                r.serverInfo = client.serverInfo();
                BenchmarkRunner runner = new BenchmarkRunner(client, cfg, p);
                runner.load(r, !noWipe);
                runner.benchmark(r);
                Path saved = store.save(r);
                System.out.println("  saved " + saved);
            });
            if (touched == 0) {
                return noneRan();
            }
            // Convenience: refresh the combined report after an 'all' run.
            ReportCmd report = new ReportCmd();
            report.resultsDir = resultsDir;
            report.renderFrom(store);
            return 0;
        }
    }

    @Command(name = "report", description = "Render RESULTS.md + results.csv from JSON results.")
    static final class ReportCmd implements Callable<Integer> {
        @Option(names = "--results-dir", defaultValue = "results")
        Path resultsDir = Path.of("results");

        @Override
        public Integer call() {
            renderFrom(new ResultStore(resultsDir));
            return 0;
        }

        void renderFrom(ResultStore store) {
            List<BenchmarkResult> all = store.loadAll();
            ReportGenerator gen = new ReportGenerator(all);
            Path md = resultsDir.resolve("RESULTS.md");
            Path csv = resultsDir.resolve("results.csv");
            gen.writeMarkdown(md);
            gen.writeCsv(csv);
            System.out.println("Wrote " + md + " and " + csv
                    + " (" + all.size() + " platform result file(s)).");
        }
    }

    // ------------------------------------------------------------------
    // Shared execution helper: connect (if creds exist) and run an action.
    // Returns the number of platforms actually touched.
    // ------------------------------------------------------------------
    @FunctionalInterface
    interface PlatformAction {
        void run(PlatformConfig p, GraphDatabaseClient client) throws Exception;
    }

    static int forEachPlatform(List<PlatformConfig> platforms, PlatformAction action) {
        if (platforms.isEmpty()) {
            System.err.println("No matching platform in platforms.yaml.");
            return 0;
        }
        int touched = 0;
        for (PlatformConfig p : platforms) {
            Optional<Configs.Credentials> creds = Configs.credentials(p);
            if (creds.isEmpty()) {
                System.out.println("SKIP " + p.displayName
                        + " — no credentials (" + p.uriEnv() + " unset).");
                continue;
            }
            System.out.println("=== " + p.displayName + " (" + p.id + ") ===");
            try (GraphDatabaseClient client = GraphDatabaseClient.connect(p, creds.get())) {
                action.run(p, client);
                touched++;
            } catch (Exception e) {
                // Honest failure reporting — never hide a failed run.
                System.err.println("FAILED " + p.displayName + ": " + e.getMessage());
            }
        }
        return touched;
    }

    static BenchmarkResult existingOrFresh(ResultStore store, Path dir, PlatformConfig p) {
        Path f = dir.resolve(p.id + ".json");
        if (java.nio.file.Files.exists(f)) {
            return store.load(f);
        }
        BenchmarkResult r = new BenchmarkResult();
        r.platformId = p.id;
        r.platformDisplayName = p.displayName;
        r.tier = p.tier;
        r.engine = p.engine;
        if (p.specs != null) {
            r.specs.putAll(p.specs);
        }
        r.runTimestampUtc = Instant.now().toString();
        return r;
    }

    static int noneRan() {
        System.err.println(
                "No platforms ran. Export credentials in the environment (see README section 4) "
                        + "or start a local target with scripts/local-neo4j.sh.");
        return 2;
    }
}
