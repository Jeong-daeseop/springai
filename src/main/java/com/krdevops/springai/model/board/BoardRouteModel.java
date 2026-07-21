package com.krdevops.springai.model.board;

/** 기존 canonical URL과 DB 등록 URL을 함께 유지하는 게시판 라우트 정보. */
public record BoardRouteModel(
        String canonicalPrefix,
        String registeredListUrl,
        String registeredListPath,
        String defaultBbsId
) {
    public boolean hasListAlias() {
        return registeredListPath != null
                && !registeredListPath.equals(canonicalPrefix + "List.do");
    }

    /**
     * 상세/등록/수정 화면이 목록 메뉴의 LNB·브레드크럼 문맥을 이어받을 때 사용할 URL.
     * DB 등록 URL이 있으면 bbsId 쿼리스트링까지 포함한 원본을 보존하고,
     * 없으면 canonical 목록 URL을 사용한다.
     */
    public String resolvedMenuContextUrl() {
        return registeredListUrl != null ? registeredListUrl : canonicalPrefix + "List.do";
    }
}
