package com.krdevops.springai.model.capture;

public record RenderedAsset(String id, String path, String mimeType, long byteLength, String contentHash) {
}
