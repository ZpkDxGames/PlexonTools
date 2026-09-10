package com.plexon.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static architectural guardrails for patterns whose mere presence in the
 * authoritative mining path is a regression. Behavioral progression and
 * provenance semantics remain covered by their focused unit tests; these checks
 * protect the absence of scheduler/DB/event-fanout anti-patterns that are much
 * easier to reintroduce accidentally than to observe in a JVM unit benchmark.
 */
final class Phase2ArchitectureContractTest {
    @Test
    void ordinaryBlockListenerDoesNotSchedulePerBreakWork() throws IOException {
        String source = source("src/main/java/com/plexon/tools/listener/ToolProgressListener.java");
        assertFalse(source.contains("runTask("));
        assertFalse(source.contains("runTaskLater("));
        assertFalse(source.contains("runTaskAsynchronously("));
        assertFalse(source.contains("CompletableFuture"));
        assertFalse(source.contains("executor.submit"));
    }

    @Test
    void ordinaryBlockListenerDoesNotReachPersistenceDirectly() throws IOException {
        String source = source("src/main/java/com/plexon/tools/listener/ToolProgressListener.java");
        assertFalse(source.contains("java.sql"));
        assertFalse(source.contains("Connection"));
        assertFalse(source.contains("PreparedStatement"));
        assertFalse(source.contains("flushAsync("));
    }

    @Test
    void areaMiningDoesNotSynthesizeRecursiveBlockBreakEvents() throws IOException {
        String source = source("src/main/java/com/plexon/tools/service/BulkBreakCoordinator.java");
        assertFalse(source.contains("new BlockBreakEvent("));
        assertTrue(source.contains("DISABLED_SAFE"));
    }

    @Test
    void unknownProvenanceCannotBeConfiguredFailOpen() throws IOException {
        String source = source("src/main/java/com/plexon/tools/service/NaturalBlockTracker.java");
        assertTrue(source.contains("case UNKNOWN ->"));
        assertTrue(source.contains("unknownDecisionCount"));
        assertFalse(source.contains("origin == Origin.UNKNOWN && !settings.naturalBlockFailClosed()"));
    }

    @Test
    void visualItemRefreshIsNotInvokedFromBlockListener() throws IOException {
        String listener = source("src/main/java/com/plexon/tools/listener/ToolProgressListener.java");
        String progression = source("src/main/java/com/plexon/tools/service/ProgressionService.java");
        assertFalse(listener.contains("refreshProgress("));
        assertTrue(progression.contains("pendingVisuals.putIfAbsent"));
    }

    @Test
    void configurationIsNotTraversedByOrdinaryBlockListener() throws IOException {
        String source = source("src/main/java/com/plexon/tools/listener/ToolProgressListener.java");
        assertFalse(source.contains("getConfig()"));
        assertFalse(source.contains("YamlConfiguration"));
        assertFalse(source.contains("ConfigurationSection"));
    }

    private static String source(String relative) throws IOException {
        Path file = Path.of(System.getProperty("user.dir")).resolve(relative);
        assertTrue(Files.isRegularFile(file), "missing source contract input: " + file);
        return Files.readString(file);
    }
}
