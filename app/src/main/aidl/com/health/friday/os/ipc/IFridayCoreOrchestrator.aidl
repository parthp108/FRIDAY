package com.health.friday.os.ipc;

import android.hardware.HardwareBuffer;

interface IFridayCoreOrchestrator {
    // Transmit lightweight structural UI nodes
    oneway void pushScreenContext(String packageName, String domHash, String layoutHierarchy);

    // Zero-copy transfer of screen pixels bypassing the 1MB Binder limit
    oneway void pushSemanticScreenshot(in HardwareBuffer buffer, String packageName);

    // System keep-alive check
    boolean ping();
}
