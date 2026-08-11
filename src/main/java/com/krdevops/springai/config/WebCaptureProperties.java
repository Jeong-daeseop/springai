package com.krdevops.springai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.web-capture")
public class WebCaptureProperties {
    private boolean enabled;
    private URI extractorBaseUrl = URI.create("http://127.0.0.1:4319");
    private String extractorApiKey = "";
    private String documentKeySecret = "";
    private int connectTimeoutSeconds = 3;
    private int responseTimeoutSeconds = 60;
    private int maxResponseMb = 50;
    private int maxUncompressedArtifactMb = 100;
    private Path artifactBasePath = Path.of(System.getProperty("java.io.tmpdir"), "springai-design-artifacts");
    private int retentionHours = 24;
    private String mapperVersion = "rendered-design-mapper-v2";
    private List<String> enabledProfiles = new ArrayList<>(List.of("LOCAL_WEB"));
    private List<String> allowedOrigins = new ArrayList<>();
    private List<String> allowedResourceOrigins = new ArrayList<>();
    private List<String> sensitiveSelectors = new ArrayList<>(List.of("input[type=password]"));

    @PostConstruct
    void validate() {
        requireRange("connect-timeout-seconds", connectTimeoutSeconds, 1, 30);
        requireRange("response-timeout-seconds", responseTimeoutSeconds, 1, 300);
        requireRange("max-response-mb", maxResponseMb, 1, 100);
        requireRange("max-uncompressed-artifact-mb", maxUncompressedArtifactMb, 1, 500);
        requireRange("retention-hours", retentionHours, 1, 168);
        if (enabled) {
            if (extractorApiKey.isBlank() || documentKeySecret.isBlank()) {
                throw new IllegalStateException("WEB_CAPTURE 활성화에는 extractor API key와 document key secret이 필요합니다.");
            }
            if (extractorApiKey.equals(documentKeySecret)) {
                throw new IllegalStateException("extractor API key와 document key secret은 달라야 합니다.");
            }
            if (!isLoopbackHttp(extractorBaseUrl)) {
                throw new IllegalStateException("Release 1 extractor-base-url은 loopback HTTP만 허용합니다.");
            }
        }
        if (enabledProfiles.stream().anyMatch(value -> !"LOCAL_WEB".equals(value))) {
            throw new IllegalStateException("Release 1은 LOCAL_WEB profile만 허용합니다.");
        }
    }

    private boolean isLoopbackHttp(URI uri) {
        try {
            return uri != null && "http".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && uri.getPath().matches("/?") && InetAddress.getByName(uri.getHost()).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalStateException("app.web-capture.%s는 %d~%d 범위여야 합니다."
                    .formatted(name, min, max));
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getExtractorBaseUrl() { return extractorBaseUrl; }
    public void setExtractorBaseUrl(URI extractorBaseUrl) { this.extractorBaseUrl = extractorBaseUrl; }
    public String getExtractorApiKey() { return extractorApiKey; }
    public void setExtractorApiKey(String value) { extractorApiKey = value == null ? "" : value; }
    public String getDocumentKeySecret() { return documentKeySecret; }
    public void setDocumentKeySecret(String value) { documentKeySecret = value == null ? "" : value; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int value) { connectTimeoutSeconds = value; }
    public int getResponseTimeoutSeconds() { return responseTimeoutSeconds; }
    public void setResponseTimeoutSeconds(int value) { responseTimeoutSeconds = value; }
    public int getMaxResponseMb() { return maxResponseMb; }
    public void setMaxResponseMb(int value) { maxResponseMb = value; }
    public int getMaxUncompressedArtifactMb() { return maxUncompressedArtifactMb; }
    public void setMaxUncompressedArtifactMb(int value) { maxUncompressedArtifactMb = value; }
    public Path getArtifactBasePath() { return artifactBasePath; }
    public void setArtifactBasePath(Path value) { artifactBasePath = value; }
    public int getRetentionHours() { return retentionHours; }
    public void setRetentionHours(int value) { retentionHours = value; }
    public String getMapperVersion() { return mapperVersion; }
    public void setMapperVersion(String value) { mapperVersion = value; }
    public List<String> getEnabledProfiles() { return enabledProfiles; }
    public void setEnabledProfiles(List<String> value) { enabledProfiles = copy(value); }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> value) { allowedOrigins = copy(value); }
    public List<String> getAllowedResourceOrigins() { return allowedResourceOrigins; }
    public void setAllowedResourceOrigins(List<String> value) { allowedResourceOrigins = copy(value); }
    public List<String> getSensitiveSelectors() { return sensitiveSelectors; }
    public void setSensitiveSelectors(List<String> value) { sensitiveSelectors = copy(value); }
    private static List<String> copy(List<String> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}
