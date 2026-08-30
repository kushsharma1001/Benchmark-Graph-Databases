package ai.wexa.gdbench;

import ai.wexa.gdbench.metrics.LatencyStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyStatsTest {

    @Test
    void percentilesUseNearestRank() {
        // 1..100 ms. Nearest-rank: p50 -> rank 50 -> value 50; p95 -> value 95; p99 -> 99.
        double[] samples = new double[100];
        for (int i = 0; i < 100; i++) {
            samples[i] = i + 1;
        }
        LatencyStats s = LatencyStats.of(samples);
        assertEquals(100, s.count());
        assertEquals(1.0, s.min());
        assertEquals(100.0, s.max());
        assertEquals(50.0, s.p50());
        assertEquals(95.0, s.p95());
        assertEquals(99.0, s.p99());
        assertEquals(50.5, s.mean());
    }

    @Test
    void singleSampleIsAllPercentiles() {
        LatencyStats s = LatencyStats.of(new double[]{42.0});
        assertEquals(42.0, s.p50());
        assertEquals(42.0, s.p95());
        assertEquals(42.0, s.p99());
        assertEquals(0.0, s.stddev());
    }

    @Test
    void emptyIsZeroed() {
        LatencyStats s = LatencyStats.of(new double[]{});
        assertEquals(0, s.count());
        assertEquals(0.0, s.p95());
    }
}
