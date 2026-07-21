package com.krdevops.springai.service;

import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.WebCaptureHealth;
import org.springframework.stereotype.Service;

import java.nio.file.Files;

@Service
public class WebCaptureHealthService {
    private final WebCaptureProperties properties;
    private final WebCaptureClient client;

    public WebCaptureHealthService(WebCaptureProperties properties, WebCaptureClient client) {
        this.properties = properties;
        this.client = client;
    }

    public WebCaptureHealth check() {
        if (!properties.isEnabled()) {
            return new WebCaptureHealth(false, false, false,
                    RenderedDesignDocument.SCHEMA_VERSION, "DISABLED");
        }
        boolean writable;
        try {
            Files.createDirectories(properties.getArtifactBasePath());
            writable = Files.isWritable(properties.getArtifactBasePath())
                    && !Files.isSymbolicLink(properties.getArtifactBasePath());
        } catch (Exception e) {
            writable = false;
        }
        boolean extractor = client.health();
        String status = extractor && writable ? "READY" : "ERROR";
        return new WebCaptureHealth(true, extractor, writable,
                RenderedDesignDocument.SCHEMA_VERSION, status);
    }
}
