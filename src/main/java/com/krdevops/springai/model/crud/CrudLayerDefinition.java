package com.krdevops.springai.model.crud;

import java.util.List;

/**
 * eGovFrame CRUD 레이어의 파일명·경로 정의 (JSP: 11개, Thymeleaf: 12개).
 *
 * <p>auto 모드(FreeMarker)와 claude 모드(프롬프트) 양쪽에서 이 정의를 공유하여
 * 레이어 경로 불일치를 방지한다.
 *
 * <p>플레이스홀더:
 * <ul>
 *   <li>{PKG}       — packageName의 "egovframework.let." 제거 후 슬래시 치환 (예: emp)</li>
 *   <li>{DOMAIN_LC} — 도메인명 소문자 시작 (예: employer)</li>
 * </ul>
 */
public record CrudLayerDefinition(
        String layerKey,
        String fileNameSuffix,
        String subPathTemplate
) {

    private static final List<CrudLayerDefinition> COMMON_LAYERS = List.of(
            new CrudLayerDefinition("vo",               "VO.java",               "src/main/java/egovframework/let/{PKG}/service/"),
            new CrudLayerDefinition("mapper",           "Mapper.java",            "src/main/java/egovframework/let/{PKG}/service/impl/"),
            new CrudLayerDefinition("mapperXml",        "Mapper.xml",             "src/main/resources/egovframework/mapper/{DOMAIN_LC}/"),
            new CrudLayerDefinition("service",          "Service.java",           "src/main/java/egovframework/let/{PKG}/service/"),
            new CrudLayerDefinition("serviceImpl",      "ServiceImpl.java",       "src/main/java/egovframework/let/{PKG}/service/impl/"),
            new CrudLayerDefinition("controller",       "Controller.java",        "src/main/java/egovframework/let/{PKG}/web/"),
            new CrudLayerDefinition("controlleradvice", "ValidationHandler.java", "src/main/java/egovframework/let/{PKG}/web/")
    );

    public static final List<CrudLayerDefinition> JSP_LAYERS = concat(
            COMMON_LAYERS,
            new CrudLayerDefinition("jspList",          "List.jsp",               "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new CrudLayerDefinition("jspDetail",        "Detail.jsp",             "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new CrudLayerDefinition("jspRegist",        "Regist.jsp",             "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new CrudLayerDefinition("jspUpdt",          "Updt.jsp",               "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/")
    );

    public static final List<CrudLayerDefinition> THYMELEAF_LAYERS = concat(
            COMMON_LAYERS,
            new CrudLayerDefinition("layoutHtml",        "layout/default.html",    "src/main/resources/templates/"),
            new CrudLayerDefinition("thymeleafList",     "List.html",              "src/main/resources/templates/{DOMAIN_LC}/"),
            new CrudLayerDefinition("thymeleafDetail",   "Detail.html",            "src/main/resources/templates/{DOMAIN_LC}/"),
            new CrudLayerDefinition("thymeleafRegist",   "Regist.html",            "src/main/resources/templates/{DOMAIN_LC}/"),
            new CrudLayerDefinition("thymeleafUpdt",     "Updt.html",              "src/main/resources/templates/{DOMAIN_LC}/")
    );

    public static final List<CrudLayerDefinition> LAYERS = JSP_LAYERS;

    public static List<CrudLayerDefinition> forViewType(CrudViewType viewType) {
        return viewType == CrudViewType.THYMELEAF ? THYMELEAF_LAYERS : JSP_LAYERS;
    }

    private static List<CrudLayerDefinition> concat(
            List<CrudLayerDefinition> common, CrudLayerDefinition... views) {
        java.util.ArrayList<CrudLayerDefinition> layers = new java.util.ArrayList<>(common);
        layers.addAll(List.of(views));
        return List.copyOf(layers);
    }

    /**
     * vo / mapper / mapperXml / service 는 {Domain}Xxx,
     * 나머지(serviceImpl, controller, handler, jsp)는 Egov{Domain}Xxx.
     */
    public static String resolveFileName(String layerKey, String domain, String suffix) {
        return switch (layerKey) {
            case "layoutHtml"                            -> "layout/default.html";
            case "vo", "mapper", "mapperXml", "service" -> domain + suffix;
            default                                      -> "Egov" + domain + suffix;
        };
    }

    /** subPathTemplate 의 플레이스홀더를 실제 값으로 치환한다. */
    public String resolveSubPath(String pkgSub, String domainLc) {
        return subPathTemplate
                .replace("{PKG}",       pkgSub)
                .replace("{DOMAIN_LC}", domainLc);
    }
}
