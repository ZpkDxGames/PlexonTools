package com.plexon.tools.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BulkBreakCoordinatorTest {
    @Test
    void parsesCompatibilityModesFailSafe() {
        assertEquals(BulkBreakCoordinator.Mode.STRICT_EVENTS,
                BulkBreakCoordinator.Mode.parse(null));
        assertEquals(BulkBreakCoordinator.Mode.STRICT_EVENTS,
                BulkBreakCoordinator.Mode.parse("strict-events"));
        assertEquals(BulkBreakCoordinator.Mode.OPTIMIZED,
                BulkBreakCoordinator.Mode.parse("optimized"));
        assertEquals(BulkBreakCoordinator.Mode.STRICT_EVENTS,
                BulkBreakCoordinator.Mode.parse("unsafe-unknown-mode"));
    }
}
