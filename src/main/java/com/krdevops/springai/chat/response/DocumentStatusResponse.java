package com.krdevops.springai.chat.response;

public record DocumentStatusResponse(
    boolean processing,
    int processedCount,
    int totalCount,
    int changedCount,
    boolean hasDocuments
) {
    public DocumentStatusResponse(boolean processing, int processedCount, int totalCount) {
        this(processing, processedCount, totalCount, 0, totalCount > 0);
    }

    public DocumentStatusResponse(boolean processing, int processedCount, int totalCount, int changedCount) {
        this(processing, processedCount, totalCount, changedCount, totalCount > 0);
    }
}
