package com.krdevops.springai.model.design;

public record FileDesignSourceMetadata(String sourcePath, String pageRange)
        implements DesignSourceMetadata {
    @Override
    public DesignSourceType sourceType() {
        return DesignSourceType.FILE;
    }
}
