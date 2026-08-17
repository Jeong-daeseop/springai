package com.krdevops.springai.service.figma;

import java.util.List;

/**
 * Figma REST API 쿼리 파라미터.
 * Pagination, filtering, depth 제어를 위한 기본 단위.
 */
public record FigmaApiQuery(
        String fileKey,
        String nodeId,
        int page,              // pagination: 0부터 시작
        int pageSize,          // default: 50, max: 100
        boolean includeChildren,
        int depth,             // default: 1, max: 10
        List<String> nodeIds
) {
    public FigmaApiQuery {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("fileKey는 필수입니다");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize는 1~100 범위여야 합니다");
        }
        if (depth < 1 || depth > 10) {
            throw new IllegalArgumentException("depth는 1~10 범위여야 합니다");
        }
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
    }

    /** nodeIds 리스트 도입(R6-040) 전 호출자 호환. */
    public FigmaApiQuery(
            String fileKey, String nodeId, int page, int pageSize, boolean includeChildren, int depth) {
        this(fileKey, nodeId, page, pageSize, includeChildren, depth, List.of());
    }

    /**
     * 기본 쿼리 생성 (단일 노드, 자식 미포함)
     */
    public static FigmaApiQuery singleNode(String fileKey, String nodeId) {
        return new FigmaApiQuery(fileKey, nodeId, 0, 50, false, 1);
    }

    /**
     * R6-040: 여러 nodeId를 pageSize 단위로 나눠 조회하는 Pagination 쿼리.
     * Figma NODES 엔드포인트는 offset/limit 개념이 없으므로, 클라이언트가 nodeIds를
     * pageSize 단위로 슬라이싱해 page번째 구간만 한 번의 GET으로 조회한다.
     */
    public static FigmaApiQuery paginated(String fileKey, List<String> nodeIds, int page, int pageSize) {
        return new FigmaApiQuery(fileKey, null, page, pageSize, false, 1, nodeIds);
    }

    /**
     * 자식 포함 쿼리
     */
    public static FigmaApiQuery withChildren(String fileKey, String nodeId, int depth) {
        return new FigmaApiQuery(fileKey, nodeId, 0, 50, true, depth);
    }

    /** nodeId(단일)와 nodeIds(다건) 중 실제로 조회할 전체 노드 ID 목록을 하나로 합쳐 반환한다. */
    public List<String> resolvedNodeIds() {
        if (!nodeIds.isEmpty()) {
            return nodeIds;
        }
        return nodeId == null || nodeId.isBlank() ? List.of() : List.of(nodeId);
    }
}
