package com.plexon.tools.integration.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class CoreAbsentLinkageTest {
    @Test
    void standaloneBoundaryLoadsWithoutResolvingCoreRuntimeTypes() {
        assertDoesNotThrow(() -> Class.forName("com.plexon.tools.integration.core.CoreBridge"));
        assertDoesNotThrow(() -> Class.forName("com.plexon.tools.integration.core.StandaloneCoreBridge"));
        assertDoesNotThrow(() -> Class.forName("com.plexon.tools.integration.core.CoreBridgeFactory"));
        assertFalse(Arrays.stream(CoreBridge.class.getMethods())
                .map(method -> method.getReturnType().getName())
                .anyMatch(name -> name.startsWith("com.zpkdxgames.plexoncore.")));
    }
}
