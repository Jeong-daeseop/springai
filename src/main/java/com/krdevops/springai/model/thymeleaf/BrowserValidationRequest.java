package com.krdevops.springai.model.thymeleaf;

import java.nio.file.Path;
import java.util.List;

/**
 * WP8 브라우저 Gate 입력. {@code url}과 {@code renderedHtml} 중 정확히 하나만 지정한다.
 * URL 입력은 Node runner에서 loopback HTTP(S)로 제한한다.
 */
public record BrowserValidationRequest(
        String screenId,
        String url,
        String renderedHtml,
        Path artifactDirectory,
        Path baselineDirectory,
        List<String> maskSelectors,
        String readySelector,
        double maxDifferenceRatio,
        int timeoutMillis
) {
    public static final double DEFAULT_MAX_DIFFERENCE_RATIO = 0.001;
    public static final int DEFAULT_TIMEOUT_MILLIS = 30_000;

    public BrowserValidationRequest {
        if (screenId == null || !screenId.matches("[A-Za-z0-9._-]{1,120}")) {
            throw new IllegalArgumentException("screenId는 영문·숫자·점·밑줄·하이픈 1~120자여야 합니다.");
        }
        boolean hasUrl = url != null && !url.isBlank();
        boolean hasHtml = renderedHtml != null && !renderedHtml.isBlank();
        if (hasUrl == hasHtml) {
            throw new IllegalArgumentException("url과 renderedHtml 중 정확히 하나만 지정해야 합니다.");
        }
        if (artifactDirectory == null) {
            throw new IllegalArgumentException("artifactDirectory는 필수입니다.");
        }
        if (maxDifferenceRatio < 0 || maxDifferenceRatio > 1) {
            throw new IllegalArgumentException("maxDifferenceRatio는 0~1 범위여야 합니다.");
        }
        if (timeoutMillis < 1_000 || timeoutMillis > 120_000) {
            throw new IllegalArgumentException("timeoutMillis는 1000~120000 범위여야 합니다.");
        }
        maskSelectors = maskSelectors == null ? List.of() : List.copyOf(maskSelectors);
    }

    public static BrowserValidationRequest forHtml(
            String screenId, String renderedHtml, Path artifactDirectory, Path baselineDirectory) {
        return new BrowserValidationRequest(screenId, null, renderedHtml, artifactDirectory, baselineDirectory,
                List.of(), null, DEFAULT_MAX_DIFFERENCE_RATIO, DEFAULT_TIMEOUT_MILLIS);
    }

    public static BrowserValidationRequest forUrl(
            String screenId, String url, Path artifactDirectory, Path baselineDirectory) {
        return new BrowserValidationRequest(screenId, url, null, artifactDirectory, baselineDirectory,
                List.of(), null, DEFAULT_MAX_DIFFERENCE_RATIO, DEFAULT_TIMEOUT_MILLIS);
    }
}
