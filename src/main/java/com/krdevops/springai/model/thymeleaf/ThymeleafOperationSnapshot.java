package com.krdevops.springai.model.thymeleaf;

import java.util.Map;

/**
 * ARCH-0402: {@code ThymeleafProjectOperation}(공개 계약)에 저장소가 필요로 하는 revision과
 * Apply 재검증에 필요한 원본 문맥(project root, 파일별 source hash, DESIGN.md revision,
 * preview hash)을 더한 영속화 단위. {@link com.krdevops.springai.service.thymeleaf.ThymeleafOperationStore}
 * 구현체가 이 레코드 전체를 저장·조회한다.
 *
 * <p>{@code ThymeleafProjectOperation} 자체에 revision을 넣지 않은 이유: 그 record는 REST/MCP
 * 응답으로 그대로 나가는 공개 계약이라 필드를 추가하면 8곳 넘는 기존 소비자를 건드려야 한다.
 * revision은 저장소 내부 관심사이므로 이 레코드에만 둔다.
 */
public record ThymeleafOperationSnapshot(
        int revision,
        ThymeleafProjectOperation operation,
        String projectRoot,
        Map<String, String> sourceHashes,
        String designRevision,
        String previewHash) {

    public ThymeleafOperationSnapshot {
        sourceHashes = sourceHashes == null ? Map.of() : Map.copyOf(sourceHashes);
    }

    public String operationId() {
        return operation.operationId();
    }
}
