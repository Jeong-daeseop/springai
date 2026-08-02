package com.krdevops.springai.service.figma;

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
        int depth              // default: 1, max: 10
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
    }

    /**
     * 기본 쿼리 생성 (단일 노드, 자식 미포함)
     */
    public static FigmaApiQuery singleNode(String fileKey, String nodeId) {
        return new FigmaApiQuery(fileKey, nodeId, 0, 50, false, 1);
    }

    /**
     * Pagination 쿼리
     */
    public static FigmaApiQuery paginated(String fileKey, int page, int pageSize) {
        return new FigmaApiQuery(fileKey, null, page, pageSize, false, 1);
    }

    /**
     * 자식 포함 쿼리
     */
    public static FigmaApiQuery withChildren(String fileKey, String nodeId, int depth) {
        return new FigmaApiQuery(fileKey, nodeId, 0, 50, true, depth);
    }
}
