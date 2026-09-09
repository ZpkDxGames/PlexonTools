package com.plexon.tools.performance;

import com.plexon.tools.performance.MiningPerformanceProfiler.Counter;
import com.plexon.tools.performance.MiningPerformanceProfiler.Isolation;
import com.plexon.tools.performance.MiningPerformanceProfiler.SampleContext;
import com.plexon.tools.performance.MiningPerformanceProfiler.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningPerformanceProfilerTest {

    @Test
    void disabledStateIsANoOp() {
        MiningPerformanceProfiler profiler = new MiningPerformanceProfiler();

        assertFalse(profiler.enabled());
        assertEquals(0L, profiler.begin());

        profiler.count(Counter.CONTEXT_HITS);
        profiler.recordElapsed(Stage.BLOCK_TOTAL, 9_000_000L);
        profiler.completeBlockSample();

        assertEquals(0L, profiler.counter(Counter.CONTEXT_HITS));
        assertEquals(0, profiler.blockSamples());
        assertTrue(profiler.reportLines().stream()
                .noneMatch(line -> line.contains("Block total")));
    }

    @Test
    void aggregatesCountersTimingsAndPercentiles() {
        MiningPerformanceProfiler profiler = new MiningPerformanceProfiler();
        profiler.startSession(0);
        for (int sample = 1; sample <= 100; sample++) {
            profiler.recordElapsed(Stage.BLOCK_TOTAL, sample * 100_000L);
            profiler.count(Counter.CONTEXT_HITS);
            profiler.completeBlockSample();
        }

        List<String> report = profiler.reportLines();
        assertEquals(100, profiler.blockSamples());
        assertEquals(100L, profiler.counter(Counter.CONTEXT_HITS));
        assertTrue(report.stream().anyMatch(line -> line.contains("Block total")));
        assertTrue(report.stream().anyMatch(line ->
                line.contains("p95") && line.contains("p99") && line.contains("max")));
        assertTrue(report.stream().anyMatch(line -> line.contains("100.00%")));
    }

    @Test
    void longSessionsKeepReportAndRetainedStatisticsBounded() {
        MiningPerformanceProfiler profiler = new MiningPerformanceProfiler();
        profiler.startSession(0);
        for (int sample = 0; sample < 10_000; sample++) {
            profiler.recordElapsed(Stage.BLOCK_MONITOR_NATURAL, 100_000L + sample);
        }

        List<String> report = profiler.reportLines();
        assertTrue(report.size() < 40,
                "The diagnostic report must remain bounded regardless of session length");
        assertTrue(report.stream().anyMatch(line ->
                line.contains("block.monitor.natural") && line.contains("p95")));
    }

    @Test
    void resetClearsCollectedMetricsWithoutChangingRunningState() {
        MiningPerformanceProfiler profiler = new MiningPerformanceProfiler();
        profiler.startSession(0);
        profiler.count(Counter.UUID_PARSES, 2L);
        profiler.recordElapsed(Stage.BLOCK_TOTAL, 1_000_000L);
        profiler.completeBlockSample();

        profiler.reset();

        assertTrue(profiler.enabled());
        assertEquals(0L, profiler.counter(Counter.UUID_PARSES));
        assertEquals(0, profiler.blockSamples());
        assertTrue(profiler.reportLines().stream()
                .noneMatch(line -> line.contains("Block total")));
    }

    @Test
    void autoStopStopsAtRequestedBlockCountAndClearsIsolation() {
        MiningPerformanceProfiler profiler = new MiningPerformanceProfiler();
        profiler.startSession(3);
        profiler.setIsolation(Isolation.VISUAL_REFRESH, true);

        profiler.completeBlockSample();
        profiler.completeBlockSample();
        assertTrue(profiler.enabled());

        profiler.completeBlockSample();

        assertFalse(profiler.enabled());
        assertEquals(3, profiler.blockSamples());
        assertFalse(profiler.isolated(Isolation.VISUAL_REFRESH));
    }

    @Test
    void tracksWorstSampleWithQueueMetadata() {
        MiningPerformanceProfiler profiler = new MiningPerformanceProfiler();
        profiler.startSession(0);
        profiler.recordElapsed(Stage.TASK_PROGRESS_EVENT_FLUSH, 2_000_000L,
                SampleContext.queues(5, 11, 7));
        profiler.recordElapsed(Stage.TASK_VISUAL_REFRESH, 80_000_000L,
                SampleContext.queues(37, 4, 18));

        List<String> report = profiler.reportLines();
        assertTrue(report.stream().anyMatch(line ->
                line.contains("Worst sample") && line.contains("task.visual_refresh")));
        assertTrue(report.stream().anyMatch(line ->
                line.contains("37/4") && line.contains("18")));
    }
}
