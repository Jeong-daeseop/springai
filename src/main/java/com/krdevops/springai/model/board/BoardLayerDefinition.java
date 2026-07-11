package com.krdevops.springai.model.board;

import com.krdevops.springai.model.crud.CrudViewType;

import java.util.List;
import java.util.Set;

/**
 * eGovFrame 게시판(BBS) 레이어의 파일명·경로 정의.
 *
 * <p>{@code CrudLayerDefinition} 과 동일한 패턴이며, 게시판 전용으로
 * {@code searchVo} 레이어가 추가된 8개 공통 레이어를 가진다.
 *
 * <p>플레이스홀더:
 * <ul>
 *   <li>{PKG}       — packageName의 "egovframework.let." 제거 후 슬래시 치환 (예: bbs)</li>
 *   <li>{DOMAIN_LC} — 도메인명 소문자 시작 (예: bbs)</li>
 * </ul>
 */
public record BoardLayerDefinition(
        String layerKey,
        String fileNameSuffix,
        String subPathTemplate
) {
    public static final Set<String> LAYOUT_LAYER_KEYS = Set.of(
            "layoutHtml",
            "layoutGnbHtml",
            "layoutLnbHtml",
            "layoutBreadcrumbHtml",
            "layoutFooterHtml"
    );

    private static final List<BoardLayerDefinition> COMMON_LAYERS = List.of(
            new BoardLayerDefinition("vo",           "VO.java",                "src/main/java/egovframework/let/{PKG}/service/"),
            new BoardLayerDefinition("searchVo",     "SearchVO.java",          "src/main/java/egovframework/let/{PKG}/service/"),
            new BoardLayerDefinition("mapper",       "Mapper.java",            "src/main/java/egovframework/let/{PKG}/service/impl/"),
            new BoardLayerDefinition("mapperXml",    "Mapper.xml",             "src/main/resources/egovframework/mapper/{DOMAIN_LC}/"),
            new BoardLayerDefinition("service",      "Service.java",           "src/main/java/egovframework/let/{PKG}/service/"),
            new BoardLayerDefinition("serviceImpl",  "ServiceImpl.java",       "src/main/java/egovframework/let/{PKG}/service/impl/"),
            new BoardLayerDefinition("controller",   "Controller.java",        "src/main/java/egovframework/let/{PKG}/web/"),
            new BoardLayerDefinition("validHandler", "ValidationHandler.java", "src/main/java/egovframework/let/{PKG}/web/")
    );

    public static final List<BoardLayerDefinition> JSP_LAYERS = concat(
            COMMON_LAYERS,
            new BoardLayerDefinition("jspList",   "List.jsp",   "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new BoardLayerDefinition("jspDetail", "Detail.jsp", "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new BoardLayerDefinition("jspRegist", "Regist.jsp", "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new BoardLayerDefinition("jspUpdt",   "Updt.jsp",   "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/")
    );

    public static final List<BoardLayerDefinition> THYMELEAF_LAYERS = concat(
            COMMON_LAYERS,
            new BoardLayerDefinition("layoutHtml",      "layout/default.html", "src/main/resources/templates/"),
            new BoardLayerDefinition("layoutGnbHtml",   "layout/gnb.html", "src/main/resources/templates/"),
            new BoardLayerDefinition("layoutLnbHtml",   "layout/lnb.html", "src/main/resources/templates/"),
            new BoardLayerDefinition("layoutBreadcrumbHtml", "layout/breadcrumb.html", "src/main/resources/templates/"),
            new BoardLayerDefinition("layoutFooterHtml", "layout/footer.html", "src/main/resources/templates/"),
            new BoardLayerDefinition("thymeleafList",   "List.html",   "src/main/resources/templates/{DOMAIN_LC}/"),
            new BoardLayerDefinition("thymeleafDetail", "Detail.html", "src/main/resources/templates/{DOMAIN_LC}/"),
            new BoardLayerDefinition("thymeleafRegist", "Regist.html", "src/main/resources/templates/{DOMAIN_LC}/"),
            new BoardLayerDefinition("thymeleafUpdt",   "Updt.html",   "src/main/resources/templates/{DOMAIN_LC}/")
    );

    public static List<BoardLayerDefinition> forViewType(CrudViewType viewType) {
        return viewType == CrudViewType.THYMELEAF ? THYMELEAF_LAYERS : JSP_LAYERS;
    }

    public static boolean isLayoutLayer(String layerKey) {
        return LAYOUT_LAYER_KEYS.contains(layerKey);
    }

    private static List<BoardLayerDefinition> concat(
            List<BoardLayerDefinition> common, BoardLayerDefinition... views) {
        java.util.ArrayList<BoardLayerDefinition> layers = new java.util.ArrayList<>(common);
        layers.addAll(List.of(views));
        return List.copyOf(layers);
    }

    /**
     * vo / searchVo / mapper / mapperXml / service 는 {Domain}Xxx,
     * 나머지(serviceImpl, controller, validHandler, view)는 Egov{Domain}Xxx.
     */
    public static String resolveFileName(String layerKey, String domain, String suffix) {
        return switch (layerKey) {
            case "layoutHtml"                                        -> "layout/default.html";
            case "layoutGnbHtml"                                     -> "layout/gnb.html";
            case "layoutLnbHtml"                                     -> "layout/lnb.html";
            case "layoutBreadcrumbHtml"                              -> "layout/breadcrumb.html";
            case "layoutFooterHtml"                                  -> "layout/footer.html";
            case "vo", "searchVo", "mapper", "mapperXml", "service" -> domain + suffix;
            default                                                  -> "Egov" + domain + suffix;
        };
    }

    /** subPathTemplate 의 플레이스홀더를 실제 값으로 치환한다. */
    public String resolveSubPath(String pkgSub, String domainLc) {
        return subPathTemplate
                .replace("{PKG}",       pkgSub)
                .replace("{DOMAIN_LC}", domainLc);
    }
}
