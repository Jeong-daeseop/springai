package com.krdevops.springai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.design-vision")
public class DesignVisionProperties {

    private String provider = "disabled";
    private String model = "gpt-4o-mini";
    private String promptVersion = "v1";
    private int maxFileMb = 20;
    private int maxPdfPages = 30;
    private int maxImagesPerRequest = 8;
    private int renderDpi = 180;
    private int maxImageDimension = 2048;
    private int timeoutSeconds = 120;
    private int maxAttempts = 2;
    private List<String> allowedPaths = new ArrayList<>();
    private Figma figma = new Figma();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getMaxFileMb() { return maxFileMb; }
    public void setMaxFileMb(int maxFileMb) { this.maxFileMb = maxFileMb; }
    public int getMaxPdfPages() { return maxPdfPages; }
    public void setMaxPdfPages(int maxPdfPages) { this.maxPdfPages = maxPdfPages; }
    public int getMaxImagesPerRequest() { return maxImagesPerRequest; }
    public void setMaxImagesPerRequest(int maxImagesPerRequest) { this.maxImagesPerRequest = maxImagesPerRequest; }
    public int getRenderDpi() { return renderDpi; }
    public void setRenderDpi(int renderDpi) { this.renderDpi = renderDpi; }
    public int getMaxImageDimension() { return maxImageDimension; }
    public void setMaxImageDimension(int maxImageDimension) { this.maxImageDimension = maxImageDimension; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public List<String> getAllowedPaths() { return allowedPaths; }
    public void setAllowedPaths(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths == null ? new ArrayList<>() : new ArrayList<>(allowedPaths);
    }
    public Figma getFigma() { return figma; }
    public void setFigma(Figma figma) { this.figma = figma == null ? new Figma() : figma; }

    @PostConstruct
    void validateFigmaConfiguration() {
        if (figma.enabled && figma.accessToken.isBlank()) {
            throw new IllegalStateException(
                    "app.design-vision.figma.enabled=true이면 FIGMA_ACCESS_TOKEN이 필요합니다.");
        }
        requireRange("depth-limit", figma.depthLimit, 1, 10);
        requireRange("connect-timeout-seconds", figma.connectTimeoutSeconds, 1, 120);
        requireRange("response-timeout-seconds", figma.responseTimeoutSeconds, 1, 300);
        requireRange("max-response-mb", figma.maxResponseMb, 1, 50);
        requireRange("max-attempts", figma.maxAttempts, 1, 3);
        requireRange("retry-max-delay-seconds", figma.retryMaxDelaySeconds, 1, 60);
        if (figma.allowedFileTypes.isEmpty()
                || figma.allowedFileTypes.stream().anyMatch(value -> value == null
                || !("file".equalsIgnoreCase(value) || "design".equalsIgnoreCase(value)))) {
            throw new IllegalStateException(
                    "app.design-vision.figma.allowed-file-types는 file/design만 허용합니다.");
        }
    }

    private void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalStateException("app.design-vision.figma.%s는 %d~%d 범위여야 합니다."
                    .formatted(name, min, max));
        }
    }

    public static class Figma {
        private boolean enabled;
        private String accessToken = "";
        private List<String> allowedFileKeys = new ArrayList<>();
        private List<String> allowedFileTypes = new ArrayList<>(List.of("file", "design"));
        private int depthLimit = 4;
        private int connectTimeoutSeconds = 5;
        private int responseTimeoutSeconds = 30;
        private int maxResponseMb = 10;
        private int maxAttempts = 3;
        private int retryMaxDelaySeconds = 15;
        private String mapperVersion = "figma-mapper-v2";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken == null ? "" : accessToken; }
        public List<String> getAllowedFileKeys() { return allowedFileKeys; }
        public void setAllowedFileKeys(List<String> allowedFileKeys) {
            this.allowedFileKeys = allowedFileKeys == null ? new ArrayList<>() : new ArrayList<>(allowedFileKeys);
        }
        public List<String> getAllowedFileTypes() { return allowedFileTypes; }
        public void setAllowedFileTypes(List<String> allowedFileTypes) {
            this.allowedFileTypes = allowedFileTypes == null
                    ? new ArrayList<>() : new ArrayList<>(allowedFileTypes);
        }
        public int getDepthLimit() { return depthLimit; }
        public void setDepthLimit(int depthLimit) { this.depthLimit = depthLimit; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }
        public int getResponseTimeoutSeconds() { return responseTimeoutSeconds; }
        public void setResponseTimeoutSeconds(int responseTimeoutSeconds) {
            this.responseTimeoutSeconds = responseTimeoutSeconds;
        }
        public int getMaxResponseMb() { return maxResponseMb; }
        public void setMaxResponseMb(int maxResponseMb) { this.maxResponseMb = maxResponseMb; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public int getRetryMaxDelaySeconds() { return retryMaxDelaySeconds; }
        public void setRetryMaxDelaySeconds(int retryMaxDelaySeconds) {
            this.retryMaxDelaySeconds = retryMaxDelaySeconds;
        }
        public String getMapperVersion() { return mapperVersion; }
        public void setMapperVersion(String mapperVersion) {
            this.mapperVersion = mapperVersion == null || mapperVersion.isBlank()
                    ? "figma-mapper-v2" : mapperVersion;
        }
    }
}
