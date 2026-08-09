package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.model.design.FigmaNodeIds;
import com.krdevops.springai.model.design.FigmaReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FigmaReferenceValidator {

    private static final String ALLOWED_HOST = "www.figma.com";
    private static final Pattern FILE_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{6,128}");

    private final DesignVisionProperties properties;

    public FigmaReference validate(String figmaUrl, String explicitNodeId) {
        if (figmaUrl == null || figmaUrl.isBlank()) {
            throw new IllegalArgumentException("Figma URL이 필요합니다.");
        }
        URI uri;
        try {
            uri = URI.create(figmaUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바른 Figma URL 형식이 아닙니다.", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !ALLOWED_HOST.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("허용되지 않은 Figma URL입니다.");
        }

        String[] segments = Arrays.stream(uri.getPath().split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length < 2) {
            throw new IllegalArgumentException("Figma URL에서 파일 정보를 찾을 수 없습니다.");
        }
        String fileType = segments[0].toLowerCase(Locale.ROOT);
        if (properties.getFigma().getAllowedFileTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .noneMatch(fileType::equals)) {
            throw new IllegalArgumentException("지원하지 않는 Figma 파일 유형입니다: " + fileType);
        }
        String fileKey = segments[1];
        if (!FILE_KEY_PATTERN.matcher(fileKey).matches()) {
            throw new IllegalArgumentException("올바른 Figma fileKey 형식이 아닙니다.");
        }
        if (!properties.getFigma().getAllowedFileKeys().isEmpty()
                && properties.getFigma().getAllowedFileKeys().stream().noneMatch(fileKey::equals)) {
            throw new SecurityException("허용되지 않은 Figma 파일입니다.");
        }

        String urlNodeId = queryParameter(uri, "node-id");
        String normalizedUrlNodeId = normalizeNodeId(urlNodeId);
        String normalizedExplicitNodeId = normalizeNodeId(explicitNodeId);
        if (normalizedUrlNodeId != null && normalizedExplicitNodeId != null
                && !normalizedUrlNodeId.equals(normalizedExplicitNodeId)) {
            throw new IllegalArgumentException("Figma URL의 node-id와 별도 nodeId가 일치하지 않습니다.");
        }
        String nodeId = normalizedExplicitNodeId != null ? normalizedExplicitNodeId : normalizedUrlNodeId;
        if (nodeId == null) {
            throw new IllegalArgumentException("분석할 Figma nodeId가 필요합니다.");
        }
        return new FigmaReference(fileKey, nodeId);
    }

    private String normalizeNodeId(String value) {
        return FigmaNodeIds.normalize(value);
    }

    private String queryParameter(URI uri, String name) {
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) return null;
        for (String pair : uri.getRawQuery().split("&")) {
            int equals = pair.indexOf('=');
            String rawName = equals < 0 ? pair : pair.substring(0, equals);
            if (name.equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) {
                return equals < 0 ? "" : pair.substring(equals + 1);
            }
        }
        return null;
    }
}
