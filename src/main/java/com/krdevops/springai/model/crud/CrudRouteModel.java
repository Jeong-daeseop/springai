package com.krdevops.springai.model.crud;

/**
 * 기존 canonical URL(packageName+domain 관례)과 DB({@code LETTNPROGRMLIST}) 등록 URL을
 * CRUD 화면(목록/상세/등록화면/등록/수정화면/수정/삭제)별로 함께 유지하는 라우트 정보.
 *
 * <p>canonical*Path는 항상 존재하며 내부 리다이렉트/링크 생성에 쓰인다.
 * registered*Path는 {@code CrudProgramMetadataService}가 DB에서 매칭한 경우에만 채워지고,
 * Controller는 canonical을 기본 매핑으로 유지한 채 registered를 추가 alias로 노출한다.
 */
public record CrudRouteModel(
        String canonicalListPath,       String registeredListPath,
        String canonicalDetailPath,     String registeredDetailPath,
        String canonicalRegistViewPath, String registeredRegistViewPath,
        String canonicalRegistPath,     String registeredRegistPath,
        String canonicalUpdtViewPath,   String registeredUpdtViewPath,
        String canonicalUpdtPath,       String registeredUpdtPath,
        String canonicalDeletePath,     String registeredDeletePath,
        /**
         * list role의 DB 원본 URL(쿼리스트링 포함) — Controller 매핑에는 쓰지 않고,
         * 메뉴 문맥 전달(menuContextUrl)에만 쓴다. {@link #resolvedMenuContextUrl()} 참고.
         */
        String registeredListUrl
) {
    public boolean hasListAlias()       { return isAlias(canonicalListPath, registeredListPath); }
    public boolean hasDetailAlias()     { return isAlias(canonicalDetailPath, registeredDetailPath); }
    public boolean hasRegistViewAlias() { return isAlias(canonicalRegistViewPath, registeredRegistViewPath); }
    public boolean hasRegistAlias()     { return isAlias(canonicalRegistPath, registeredRegistPath); }
    public boolean hasUpdtViewAlias()   { return isAlias(canonicalUpdtViewPath, registeredUpdtViewPath); }
    public boolean hasUpdtAlias()       { return isAlias(canonicalUpdtPath, registeredUpdtPath); }
    public boolean hasDeleteAlias()     { return isAlias(canonicalDeletePath, registeredDeletePath); }

    public boolean hasAnyAlias() {
        return hasListAlias() || hasDetailAlias() || hasRegistViewAlias() || hasRegistAlias()
                || hasUpdtViewAlias() || hasUpdtAlias() || hasDeleteAlias();
    }

    /**
     * 화면 내부 링크/폼 action에 사용할 실제 URL — alias가 있으면 등록 URL을,
     * 없으면 canonical URL을 반환한다. EgovGnbMenuInterceptor가 요청 경로와
     * LETTNPROGRMLIST.URL을 비교해 메뉴 문맥을 채우므로, 화면 간 이동 시에도
     * 이 값을 써야 브레드크럼/LNB 문맥이 유지된다.
     */
    public String resolvedListPath()       { return hasListAlias() ? registeredListPath : canonicalListPath; }
    public String resolvedDetailPath()     { return hasDetailAlias() ? registeredDetailPath : canonicalDetailPath; }
    public String resolvedRegistViewPath() { return hasRegistViewAlias() ? registeredRegistViewPath : canonicalRegistViewPath; }
    public String resolvedRegistPath()     { return hasRegistAlias() ? registeredRegistPath : canonicalRegistPath; }
    public String resolvedUpdtViewPath()   { return hasUpdtViewAlias() ? registeredUpdtViewPath : canonicalUpdtViewPath; }
    public String resolvedUpdtPath()       { return hasUpdtAlias() ? registeredUpdtPath : canonicalUpdtPath; }
    public String resolvedDeletePath()     { return hasDeleteAlias() ? registeredDeletePath : canonicalDeletePath; }

    /**
     * 화면 간 이동 시 EgovGnbMenuInterceptor에 넘길 메뉴 문맥 식별 URL.
     * registeredListUrl(쿼리스트링 포함 DB 원본)이 있으면 그대로 쓰고, 없으면 canonical list path를
     * 쓴다 — path만으로 비교하면 bbsId만 다른 여러 게시판 메뉴를 구분하지 못하므로 반드시
     * 쿼리스트링까지 보존된 이 값을 인터셉터의 menu.getUrl()과 정확히 비교해야 한다.
     */
    public String resolvedMenuContextUrl() {
        return registeredListUrl != null ? registeredListUrl : canonicalListPath;
    }

    private static boolean isAlias(String canonical, String registered) {
        return registered != null && !registered.equals(canonical);
    }

    /** DB 매칭이 없을 때(fallback) — canonical만 채운 라우트. */
    public static CrudRouteModel canonicalOnly(String urlPrefix) {
        return new CrudRouteModel(
                urlPrefix + "List.do", null,
                urlPrefix + "Detail.do", null,
                urlPrefix + "RegistView.do", null,
                urlPrefix + "Regist.do", null,
                urlPrefix + "UpdtView.do", null,
                urlPrefix + "Updt.do", null,
                urlPrefix + "Delete.do", null,
                null);
    }
}