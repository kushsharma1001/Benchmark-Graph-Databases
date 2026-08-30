package ai.wexa.gdbench.metrics;

/**
 * Collects latency samples for one workload. Not thread-safe; each worker thread
 * uses its own Timer and the caller merges the sample arrays.
 */
public final class Timer {

    private double[] samples;
    private int size = 0;

    public Timer(int expectedSamples) {
        this.samples = new double[Math.max(16, expectedSamples)];
    }

    /** Record a single elapsed measurement in nanoseconds; stored as milliseconds. */
    public void recordNanos(long nanos) {
        if (size == samples.length) {
            double[] grown = new double[samples.length * 2];
            System.arraycopy(samples, 0, grown, 0, samples.length);
            samples = grown;
        }
        samples[size++] = nanos / 1_000_000.0;
    }

    /** Time a runnable, record it, and return its elapsed nanos. */
    public long time(Runnable r) {
        long t0 = System.nanoTime();
        r.run();
        long dt = System.nanoTime() - t0;
        recordNanos(dt);
        return dt;
    }

    public double[] samplesMs() {
        double[] out = new double[size];
        System.arraycopy(samples, 0, out, 0, size);
        return out;
    }

    public int size() {
        return size;
    }

    public LatencyStats stats() {
        return LatencyStats.of(samplesMs());
    }
}
