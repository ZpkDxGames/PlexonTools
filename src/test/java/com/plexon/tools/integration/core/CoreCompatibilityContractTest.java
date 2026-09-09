package com.plexon.tools.integration.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CoreCompatibilityContractTest {
    @Test
    void stableLocalAuthorityLineSupportsCoreOneAndTwo() {
        assertEquals(">=1.0 <3.0", CoreBridge.SUPPORTED_API_RANGE);
        assertEquals("tools", CoreBridge.MODULE_ID);
    }
}
