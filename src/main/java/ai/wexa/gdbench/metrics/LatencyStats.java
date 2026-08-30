package ai.wexa.gdbench.metrics;

import java.util.Arrays;

/**
 * Percentile latency statistics over a set of timing samples (milliseconds).
 *
 * <p>The assignment explicitly asks for percentiles, not just averages, and for
 * &ge; 100 iterations per read workload. p95/p99 are what expose free-tier tail
 * behaviour (throttling, GC pauses, network jitter) that a mean would hide.
 *
 * <p>Percentiles use the nearest-rank method on the sorted samples, which is
 * unambiguous and does not interpolate — appropriate for reporting observed
 * latencies rather than estimating a continuous distribution.
 */
public final class LatencyStats {

    private final int count;
    private final double min;
    private final double max;
    private final double mean;
    private final double p50;
    private final double p95;
    private final double p99;
    private final double stddev;

    private LatencyStats(int count, double min, double max, double mean,
                         double p50, double p95, double p99, double stddev) {
        this.count = count;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.p50 = p50;
        this.p95 = p95;
        this.p99 = p99;
        this.stddev = stddev;
    }

    /** Compute statistics from raw latency samples in milliseconds. */
    public static LatencyStats of(double[] samplesMs) {
        if (samplesMs == null || samplesMs.length == 0) {
            return new LatencyStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
        double[] sorted = samplesMs.clone();
        Arrays.sort(sorted);

        int n = sorted.length;
        double sum = 0;
        for (double v : sorted) {
            sum += v;
        }
        double mean = sum / n;

        double sq = 0;
        for (double v : sorted) {
            sq += (v - mean) * (v - mean);
        }
        double stddev = Math.sqrt(sq / n);

        return new LatencyStats(
                n,
                round(sorted[0]),
                round(sorted[n - 1]),
                round(mean),
                round(percentile(sorted, 50)),
                round(percentile(sorted, 95)),
                round(percentile(sorted, 99)),
                round(stddev));
    }

    /** Nearest-rank percentile over an already-sorted array. */
    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        // rank = ceil(p/100 * N), clamped to [1, N]; index = rank - 1.
        int rank = (int) Math.ceil((p / 100.0) * sorted.length);
        rank = Math.max(1, Math.min(sorted.length, rank));
        return sorted[rank - 1];
    }

    public int count()    { return count; }
    public double min()   { return min; }
    public double max()   { return max; }
    public double mean()  { return mean; }
    public double p50()   { return p50; }
    public double p95()   { return p95; }
    public double p99()   { return p99; }
    public double stddev(){ return stddev; }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    @Override
    public String toString() {
        return "n=" + count + " p50=" + p50 + "ms p95=" + p95 + "ms p99=" + p99 + "ms";
    }
}
