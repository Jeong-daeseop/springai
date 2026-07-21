package com.krdevops.springai.model.capture;

public record WebCaptureHealth(boolean enabled, boolean extractorReady,
                               boolean artifactPathWritable, String schemaVersion, String status) {
}
