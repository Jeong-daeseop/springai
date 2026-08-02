package com.krdevops.springai.service.figma;

import com.krdevops.springai.config.DesignVisionProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Figma 파일 및 노드 화이트리스트 검증.
 * 2-A2 Allowlist 검증 구현
 */
@Component
public class FigmaFileAllowlistValidator {

    private final DesignVisionProperties properties;
    private Set<String> allowedFileKeys;

    public FigmaFileAllowlistValidator(DesignVisionProperties properties) {
        this.properties = properties;
        this.allowedFileKeys = loadAllowedFileKeys();
    }

    /**
     * fileKey가 허용 목록에 있는지 검증한다.
     * allowlist가 비어 있으면 모든 파일을 허용한다 (개발 모드).
     */
    public boolean isFileKeyAllowed(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return false;
        }

        // 비어 있으면 모든 파일 허용 (개발 모드)
        if (allowedFileKeys.isEmpty()) {
            return true;
        }

        return allowedFileKeys.contains(fileKey);
    }

    /**
     * 파일 키가 허용되지 않으면 예외를 던진다.
     */
    public void validateFileKey(String fileKey) {
        if (!isFileKeyAllowed(fileKey)) {
            throw new FigmaAllowlistException(
                    "FIGMA_FILE_NOT_ALLOWED",
                    "Figma 파일 접근이 허용되지 않습니다: " + fileKey
            );
        }
    }

    /**
     * Node ID가 허용되는지 검증한다 (현재: 단순 형식 검증만 수행).
     * 추가 검증 (소속 확인, 캐시 등)은 후속 구현
     */
    public boolean isNodeIdAllowed(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return false;
        }

        // 기본 형식: "1:2" 또는 "123" (숫자:숫자 또는 숫자)
        return nodeId.matches("^\\d+(?::\\d+)?$");
    }

    /**
     * Node ID 형식이 유효하지 않으면 예외를 던진다.
     */
    public void validateNodeId(String nodeId) {
        if (!isNodeIdAllowed(nodeId)) {
            throw new FigmaAllowlistException(
                    "FIGMA_NODE_INVALID",
                    "Figma Node ID 형식이 유효하지 않습니다: " + nodeId
            );
        }
    }

    private Set<String> loadAllowedFileKeys() {
        var allowedKeysList = properties.getFigma().getAllowedFileKeys();
        if (allowedKeysList == null || allowedKeysList.isEmpty()) {
            return Collections.emptySet();
        }

        return new HashSet<>(allowedKeysList);
    }

    /**
     * 설정 갱신 시 호출 (동적 리로드).
     */
    public void reloadAllowlist() {
        this.allowedFileKeys = loadAllowedFileKeys();
    }

    public static class FigmaAllowlistException extends RuntimeException {
        private final String code;

        public FigmaAllowlistException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
