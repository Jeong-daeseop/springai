package com.krdevops.springai.service;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Figma {@code queryImages()}가 반환한 CDN URL에서 아이콘/이미지 asset을 다운로드해 생성
 * 프로젝트에 저장한다 — Figma_픽셀재현_제외범위_구현계획.md 트랙 B.
 *
 * <p>⚠ {@link #ALLOWED_HOST_SUFFIX}는 이 세션에서 실제 Figma API 응답으로 확인하지 못한
 * 추정치다(FIGMA_ACCESS_TOKEN이 설정된 실 연동 환경이 없어 라이브 검증 불가 — 사용자 승인 하에
 * 훈련 지식 기반 도메인으로 진행). 실사용 전 실제 응답 호스트와 일치하는지 반드시 재확인해야
 * 하며, 다르면 모든 다운로드가 {@link #validateHost(String)}에서 차단된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigmaAssetDownloadService {

    /** ⚠ 미검증 추정치 — 클래스 Javadoc 참고. */
    private static final String ALLOWED_HOST_SUFFIX = ".figma.com";
    private static final long MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024;
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("^\\d+:\\d+$");
    private static final String ASSET_DIRECTORY = "src/main/resources/static/images/figma/";

    private final FigmaApiClient figmaApiClient;
    private final ApprovedProjectWritePort writePort;
    private final CodeService codeService;

    /**
     * @param fileKey  Figma 파일 키
     * @param nodeIds  다운로드할 이미지/벡터 노드 id 목록(형식: "숫자:숫자")
     * @param outputPath 생성 프로젝트 루트(승인된 출력 경로)
     * @return 실제로 저장된 파일의 상대경로 목록(렌더 실패 노드는 건너뛰고 조용히 제외)
     */
    public List<String> downloadAssets(String fileKey, List<String> nodeIds, String outputPath) {
        for (String nodeId : nodeIds) {
            if (!NODE_ID_PATTERN.matcher(nodeId).matches()) {
                throw new IllegalArgumentException("올바르지 않은 Figma nodeId 형식입니다: " + nodeId);
            }
        }
        codeService.validateOutputRoot(outputPath);
        if (nodeIds.isEmpty()) return List.of();

        FigmaApiClient.FigmaImageUrls images = figmaApiClient.queryImages(fileKey, nodeIds);
        List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
        List<String> savedPaths = new ArrayList<>();
        for (String nodeId : nodeIds) {
            String url = images.imageUrlsByNodeId().get(nodeId);
            if (url == null) {
                log.warn("Figma 이미지 렌더 실패로 건너뜀: nodeId={}", nodeId);
                continue;
            }
            validateHost(url);
            byte[] bytes = download(url);
            String relativePath = ASSET_DIRECTORY + sanitizeNodeId(nodeId) + "." + extensionOf(url);
            changes.add(new ProjectChangeSet.FileChange(relativePath, "MISSING", "", null, bytes));
            savedPaths.add(relativePath);
        }
        if (changes.isEmpty()) return List.of();

        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath, null, changes, List.of(), ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);
        if (outcome.status() != ApplyOutcome.Status.APPLIED) {
            throw new IllegalStateException("Figma 이미지 저장 실패(" + outcome.status() + "): "
                    + (outcome.failureDetail() != null ? outcome.failureDetail() : outcome.conflictingPaths()));
        }
        return List.copyOf(savedPaths);
    }

    private void validateHost(String url) {
        String host = URI.create(url).getHost();
        if (host == null || !host.toLowerCase(Locale.ROOT).endsWith(ALLOWED_HOST_SUFFIX)) {
            throw new SecurityException("허용되지 않은 이미지 호스트입니다: " + host);
        }
    }

    /** package-private로 열어 테스트에서 실제 네트워크 I/O 없이 오버라이드할 수 있게 한다
     * (FigmaApiClient가 HttpClient를 주입받는 것과 같은 취지 — 이 경우 메서드 자체를 연다). */
    byte[] download(String url) {
        try {
            URLConnection connection = URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = in.readNBytes((int) MAX_DOWNLOAD_BYTES + 1);
                if (bytes.length > MAX_DOWNLOAD_BYTES) {
                    throw new IllegalStateException("이미지 크기가 허용 한도(" + MAX_DOWNLOAD_BYTES + " bytes)를 초과합니다.");
                }
                return bytes;
            }
        } catch (IOException e) {
            throw new IllegalStateException("이미지 다운로드 실패: " + e.getMessage(), e);
        }
    }

    private String extensionOf(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "jpg";
        if (lower.contains(".svg")) return "svg";
        return "png";
    }

    private String sanitizeNodeId(String nodeId) {
        return nodeId.replace(":", "-");
    }
}
