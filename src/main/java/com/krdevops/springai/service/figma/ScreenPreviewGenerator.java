package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * I-6D: Thymeleaf 화면 preview Figma 내 embed.
 */
@Service
public class ScreenPreviewGenerator {

    /**
     * Thymeleaf HTML을 Figma board comment로 embed하기 위한 markdown 생성.
     */
    public String generateFigmaBoardComment(String screenName, String htmlContent, String templatePath) {
        String embedId = UUID.randomUUID().toString();

        return String.format(
            """
            ## Thymeleaf Conversion Preview

            **Screen**: %s
            **Template**: %s
            **Preview ID**: %s

            ### HTML Structure
            ```html
            %s
            ```

            ### Status
            - [ ] Design matches Figma mockup
            - [ ] All fields correctly bound
            - [ ] Responsive layout verified
            - [ ] Accessibility checked
            """,
            screenName, templatePath, embedId,
            truncateHtml(htmlContent, 500)
        );
    }

    /**
     * Thymeleaf HTML을 iframe embed URL로 변환.
     */
    public String generatePreviewEmbedUrl(String htmlContent, String screenName) {
        String previewId = UUID.randomUUID().toString();
        String encodedHtml = encodeForUrl(htmlContent);

        return String.format("/preview/%s?screen=%s&html=%s",
            previewId, screenName, encodedHtml);
    }

    /**
     * Figma frame에 embed할 preview 메타데이터 생성.
     */
    public Map<String, Object> generatePreviewMetadata(
            String figmaFileId,
            String figmaNodeId,
            String screenName,
            String previewUrl) {

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("figmaFileId", figmaFileId);
        metadata.put("figmaNodeId", figmaNodeId);
        metadata.put("screenName", screenName);
        metadata.put("previewUrl", previewUrl);
        metadata.put("previewType", "IFRAME");
        metadata.put("generatedAt", System.currentTimeMillis());
        metadata.put("status", "READY_FOR_EMBED");

        return metadata;
    }

    /**
     * Figma DevMode용 preview 스냅샷 생성.
     */
    public Map<String, Object> generateDevModeSnapshot(String htmlContent, String screenName) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("screenName", screenName);
        snapshot.put("htmlFingerprint", computeFingerprint(htmlContent));
        snapshot.put("componentCount", countComponents(htmlContent));
        snapshot.put("fieldCount", countFields(htmlContent));
        snapshot.put("timestamp", System.currentTimeMillis());
        snapshot.put("status", "SNAPSHOT_CREATED");

        return snapshot;
    }

    /**
     * Preview의 viewport별 렌더링 옵션 생성.
     */
    public Map<String, Map<String, Object>> generateViewportPreviewOptions() {
        Map<String, Map<String, Object>> options = new HashMap<>();

        Map<String, Object> desktop = new HashMap<>();
        desktop.put("width", 1440);
        desktop.put("height", 900);
        desktop.put("scale", 1.0);
        options.put("DESKTOP", desktop);

        Map<String, Object> tablet = new HashMap<>();
        tablet.put("width", 768);
        tablet.put("height", 1024);
        tablet.put("scale", 0.75);
        options.put("TABLET", tablet);

        Map<String, Object> mobile = new HashMap<>();
        mobile.put("width", 390);
        mobile.put("height", 844);
        mobile.put("scale", 0.5);
        options.put("MOBILE", mobile);

        return options;
    }

    // ===== Helper Methods =====

    private String truncateHtml(String html, int maxLength) {
        if (html.length() <= maxLength) {
            return html;
        }
        return html.substring(0, maxLength) + "...";
    }

    private String encodeForUrl(String html) {
        return java.net.URLEncoder.encode(html, java.nio.charset.StandardCharsets.UTF_8)
            .replace("+", "%20");
    }

    private String computeFingerprint(String htmlContent) {
        return String.valueOf(htmlContent.hashCode());
    }

    private int countComponents(String htmlContent) {
        return htmlContent.split("th:object").length - 1;
    }

    private int countFields(String htmlContent) {
        return htmlContent.split("th:field").length - 1;
    }
}
