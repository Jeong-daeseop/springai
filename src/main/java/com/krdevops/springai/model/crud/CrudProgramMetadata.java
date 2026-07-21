package com.krdevops.springai.model.crud;

import java.util.Map;

/**
 * {@code LETTNPROGRMLIST}/{@code LETTNMENUINFO} 조회 결과를 CRUD 화면별 role로 해석한 결과.
 * 생성기는 이 정보를 읽기만 한다 (DB에 쓰지 않는다).
 *
 * <p>role 키: {@code list}, {@code detail}, {@code registView}, {@code regist},
 * {@code updtView}, {@code updt}, {@code delete}.
 */
public record CrudProgramMetadata(
        String programFileName,
        String programStorePath,
        String programKoreanName,
        String upperMenuName,
        Map<String, String> registeredPathByRole,
        /**
         * list role의 DB 원본 URL(쿼리스트링 포함, 예: "...?bbsId=BBS_NOTICE"). {@code
         * registeredPathByRole}의 값은 Controller {@code @GetMapping} 매핑용으로 항상 path만
         * 담기 때문에, path가 같고 bbsId 같은 식별 파라미터만 다른 여러 메뉴(흔한 게시판 패턴)를
         * 구분하려면 이 전체 URL이 별도로 필요하다 — EgovGnbMenuInterceptor가 LETTNMENUINFO의
         * 원본 URL과 그대로 비교하는 데 쓰인다.
         */
        String registeredListUrl,
        Source source,
        Status status,
        String message
) {
    public enum Source { EXPLICIT, DATABASE, FALLBACK }
    public enum Status { RESOLVED, FALLBACK, AMBIGUOUS }

    public CrudProgramMetadata {
        registeredPathByRole = registeredPathByRole == null ? Map.of() : Map.copyOf(registeredPathByRole);
        source = source == null ? Source.FALLBACK : source;
        status = status == null ? Status.FALLBACK : status;
    }

    public static CrudProgramMetadata fallback(String message) {
        return new CrudProgramMetadata(null, null, null, null, Map.of(), null,
                Source.FALLBACK, Status.FALLBACK, message);
    }

    /** list role이 여러 후보로 모호해 생성 자체를 막아야 하는 경우에만 true. */
    public boolean blocksGeneration() {
        return status == Status.AMBIGUOUS;
    }

    public String registeredPath(String role) {
        return registeredPathByRole.get(role);
    }

    public String menuIntegrationStatus() {
        if (blocksGeneration()) return "연동 실패";
        if (registeredPathByRole.isEmpty()) return "기존 규칙 fallback";
        return upperMenuName == null ? "DB URL 연동(메뉴 미연결)" : "DB URL + GNB/LNB 연동";
    }
}
