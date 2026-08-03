package com.krdevops.springai.service.artifact;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import org.springframework.stereotype.Component;

/**
 * ARCH-0507: Artifact 다운로드 authorization과 Content-Disposition 정책.
 * 현재 이 정책을 호출할 공통 REST 다운로드 엔드포인트는 아직 없다(소비자 미확정이라
 * controller 결선은 범위 밖) — 정책 로직만 독립적으로 검증 가능하게 분리해 향후
 * controller에서 그대로 재사용한다.
 */
@Component
public class ArtifactDownloadPolicy {

    public boolean isDownloadable(Artifact artifact) {
        return artifact != null && artifact.status() == ArtifactStatus.ACTIVE;
    }

    public String contentDisposition(Artifact artifact) {
        if (!isDownloadable(artifact)) {
            throw new IllegalStateException("ACTIVE 상태가 아닌 artifact는 다운로드할 수 없습니다: " + artifact.artifactId());
        }
        return "attachment; filename=\"%s%s\"".formatted(artifact.artifactId(), extensionFor(artifact.mediaType()));
    }

    private String extensionFor(String mediaType) {
        return switch (mediaType) {
            case "application/json" -> ".json";
            case "text/html" -> ".html";
            case "text/plain" -> ".txt";
            case "application/zip" -> ".zip";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> "";
        };
    }
}
