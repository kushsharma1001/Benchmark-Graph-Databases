package ai.wexa.gdbench.report;

import ai.wexa.gdbench.metrics.BenchmarkResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the combined results matrix from every platform's JSON into:
 * <ul>
 *   <li>{@code results/RESULTS.md} — the human-readable matrix (section 5.2 tables)
 *       that gets pasted/linked into the README.</li>
 *   <li>{@code results/results.csv} — long-format rows for charting.</li>
 * </ul>
 *
 * <p>Everything is data-driven from the loaded results, so adding a platform or a
 * hop depth needs no report changes.
 */
public final class ReportGenerator {

    private final List<BenchmarkResult> results;

    public ReportGenerator(List<BenchmarkResult> results) {
        this.results = results;
    }

    public void writeMarkdown(Path out) {
        StringBuilder md = new StringBuilder();
        md.append("# Benchmark Results Matrix\n\n");
        if (results.isEmpty()) {
            md.append("_No results found. Run the benchmark first._\n");
            writeFile(out, md.toString());
            return;
        }
        md.append("Generated from ").append(results.size())
                .append(" platform result file(s). Latencies in **milliseconds**; ")
                .append("lower is better. Throughput in **ops/sec**; higher is better.\n\n");

        platformSummary(md);
        loadTable(md);
        traversalTable(md);
        lookupTable(md);
        aggregationTable(md);
        mixedTable(md);
        footprintTable(md);
        caveatsSection(md);

        writeFile(out, md.toString());
    }

    private void platformSummary(StringBuilder md) {
        md.append("## Platforms & tiers\n\n");
        md.append("| Platform | Tier | Engine | vCPU | RAM | Disk | Server |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (BenchmarkResult r : results) {
            md.append("| ").append(nz(r.platformDisplayName))
                    .append(" | ").append(nz(r.tier))
                    .append(" | ").append(nz(r.engine))
                    .append(" | ").append(spec(r, "vcpu"))
                    .append(" | ").append(spec(r, "ram"))
                    .append(" | ").append(spec(r, "disk"))
                    .append(" | ").append(nz(r.serverInfo))
                    .append(" |\n");
        }
        md.append("\n");
    }

    private void loadTable(StringBuilder md) {
        md.append("## Data loading (ingest throughput)\n\n");
        md.append("| Platform | Nodes | Rels | Load time (s) | Nodes/s | Rels/s |\n");
        md.append("|---|--:|--:|--:|--:|--:|\n");
        for (BenchmarkResult r : results) {
            var l = r.load;
            md.append("| ").append(nz(r.platformDisplayName))
                    .append(" | ").append(l.nodes)
                    .append(" | ").append(l.relationships)
                    .append(" | ").append(fmt(l.wallClockSeconds))
                    .append(" | ").append(fmt(l.nodesPerSecond))
                    .append(" | ").append(fmt(l.relationshipsPerSecond))
                    .append(" |\n");
        }
        md.append("\n");
    }

    private void traversalTable(StringBuilder md) {
        md.append("## Traversal latency (p50 / p95 ms)\n\n");
        // Collect the set of hop keys across all results, in insertion order.
        Set<String> hopKeys = new LinkedHashSet<>();
        for (BenchmarkResult r : results) {
            hopKeys.addAll(r.traversal.keySet());
        }
        md.append("| Platform |");
        for (String h : hopKeys) {
            md.append(" ").append(h).append(" p50 | ").append(h).append(" p95 |");
        }
        md.append("\n|---|");
        for (int i = 0; i < hopKeys.size(); i++) {
            md.append("--:|--:|");
        }
        md.append("\n");
        for (BenchmarkResult r : results) {
            md.append("| ").append(nz(r.platformDisplayName)).append(" |");
            for (String h : hopKeys) {
                BenchmarkResult.StatsView v = r.traversal.get(h);
                md.append(" ").append(v == null ? "—" : fmt(v.p50))
                        .append(" | ").append(v == null ? "—" : fmt(v.p95)).append(" |");
            }
            md.append("\n");
        }
        md.append("\n");
    }

    private void lookupTable(StringBuilder md) {
        md.append("## Lookups (p50 / p95 ms)\n\n");
        md.append("| Platform | Point p50 | Point p95 | Indexed p50 | Indexed p95 | Indexed on |\n");
        md.append("|---|--:|--:|--:|--:|---|\n");
        for (BenchmarkResult r : results) {
            md.append("| ").append(nz(r.platformDisplayName))
                    .append(" | ").append(stat(r.pointLookup, true))
                    .append(" | ").append(stat(r.pointLookup, false))
                    .append(" | ").append(stat(r.indexedLookup, true))
                    .append(" | ").append(stat(r.indexedLookup, false))
                    .append(" | ").append(nz(r.indexedProperties))
                    .append(" |\n");
        }
        md.append("\n");
    }

    private void aggregationTable(StringBuilder md) {
        md.append("## Aggregation (p50 / p95 ms)\n\n");
        md.append("| Platform | Query | p50 | p95 | p99 |\n");
        md.append("|---|---|--:|--:|--:|\n");
        for (BenchmarkResult r : results) {
            var a = r.aggregation;
            md.append("| ").append(nz(r.platformDisplayName))
                    .append(" | ").append(nz(r.aggregationDescription))
                    .append(" | ").append(a == null ? "—" : fmt(a.p50))
                    .append(" | ").append(a == null ? "—" : fmt(a.p95))
                    .append(" | ").append(a == null ? "—" : fmt(a.p99))
                    .append(" |\n");
        }
        md.append("\n");
    }

    private void mixedTable(StringBuilder md) {
        md.append("## Mixed read/write workload (throughput ops/s, per-op p95 ms)\n\n");
        Set<String> levels = new LinkedHashSet<>();
        for (BenchmarkResult r : results) {
            levels.addAll(r.mixed.keySet());
        }
        md.append("| Platform |");
        for (String lvl : levels) {
            md.append(" ").append(lvl).append(" ops/s | ").append(lvl).append(" p95 |");
        }
        md.append("\n|---|");
        for (int i = 0; i < levels.size(); i++) {
            md.append("--:|--:|");
        }
        md.append("\n");
        for (BenchmarkResult r : results) {
            md.append("| ").append(nz(r.platformDisplayName)).append(" |");
            for (String lvl : levels) {
                BenchmarkResult.MixedMetrics m = r.mixed.get(lvl);
                md.append(" ").append(m == null ? "—" : fmt(m.throughputOpsPerSecond))
                        .append(" | ").append(m == null || m.latency == null ? "—" : fmt(m.latency.p95))
                        .append(" |");
            }
            md.append("\n");
        }
        md.append("\n");
    }

    private void footprintTable(StringBuilder md) {
        md.append("## Footprint\n\n");
        md.append("| Platform | Nodes stored | Rels stored | Stored size | Memory |\n");
        md.append("|---|--:|--:|---|---|\n");
        for (BenchmarkResult r : results) {
            md.append("| ").append(nz(r.platformDisplayName))
                    .append(" | ").append(fp(r, "nodesStored"))
                    .append(" | ").append(fp(r, "relationshipsStored"))
                    .append(" | ").append(fp(r, "storedDataSize"))
                    .append(" | ").append(fp(r, "memoryUsage"))
                    .append(" |\n");
        }
        md.append("\n");
    }

    private void caveatsSection(StringBuilder md) {
        boolean any = results.stream().anyMatch(r -> r.caveats != null && !r.caveats.isEmpty());
        if (!any) {
            return;
        }
        md.append("## Recorded caveats\n\n");
        for (BenchmarkResult r : results) {
            if (r.caveats == null || r.caveats.isEmpty()) {
                continue;
            }
            md.append("**").append(nz(r.platformDisplayName)).append("**\n\n");
            r.caveats.forEach((k, v) -> md.append("- `").append(k).append("`: ").append(v).append("\n"));
            md.append("\n");
        }
    }

    // ------------------------------------------------------------------
    // CSV (long format): platform,metric,submetric,value
    // ------------------------------------------------------------------
    public void writeCsv(Path out) {
        List<String> rows = new ArrayList<>();
        rows.add("platform,category,metric,value");
        for (BenchmarkResult r : results) {
            String p = csv(r.platformDisplayName);
            rows.add(row(p, "load", "nodes_per_second", r.load.nodesPerSecond));
            rows.add(row(p, "load", "rels_per_second", r.load.relationshipsPerSecond));
            rows.add(row(p, "load", "wall_clock_seconds", r.load.wallClockSeconds));
            r.traversal.forEach((h, v) -> {
                rows.add(row(p, "traversal", h + "_p50", v.p50));
                rows.add(row(p, "traversal", h + "_p95", v.p95));
            });
            if (r.pointLookup != null) {
                rows.add(row(p, "lookup", "point_p50", r.pointLookup.p50));
                rows.add(row(p, "lookup", "point_p95", r.pointLookup.p95));
            }
            if (r.indexedLookup != null) {
                rows.add(row(p, "lookup", "indexed_p50", r.indexedLookup.p50));
                rows.add(row(p, "lookup", "indexed_p95", r.indexedLookup.p95));
            }
            if (r.aggregation != null) {
                rows.add(row(p, "aggregation", "p50", r.aggregation.p50));
                rows.add(row(p, "aggregation", "p95", r.aggregation.p95));
            }
            r.mixed.forEach((lvl, m) -> {
                rows.add(row(p, "mixed", lvl + "_ops_per_sec", m.throughputOpsPerSecond));
                if (m.latency != null) {
                    rows.add(row(p, "mixed", lvl + "_p95", m.latency.p95));
                }
            });
        }
        writeFile(out, String.join("\n", rows) + "\n");
    }

    // ---- helpers ----
    private static String row(String p, String cat, String metric, double value) {
        return p + "," + cat + "," + metric + "," + value;
    }

    private static String stat(BenchmarkResult.StatsView v, boolean p50) {
        if (v == null) {
            return "—";
        }
        return fmt(p50 ? v.p50 : v.p95);
    }

    private static String spec(BenchmarkResult r, String key) {
        return r.specs == null ? "—" : nz(r.specs.getOrDefault(key, "—"));
    }

    private static String fp(BenchmarkResult r, String key) {
        return r.footprint == null ? "—" : nz(r.footprint.getOrDefault(key, "—"));
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return String.format("%.2f", v);
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static String csv(String s) {
        String v = nz(s);
        return v.contains(",") ? "\"" + v + "\"" : v;
    }

    private static void writeFile(Path out, String content) {
        try {
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.writeString(out, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report " + out, e);
        }
    }
}
