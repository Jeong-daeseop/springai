package com.krdevops.springai.model.crud;

import java.util.List;

/**
 * eGovFrame CRUD 11개 레이어의 파일명·경로 정의.
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

    public static final List<CrudLayerDefinition> LAYERS = List.of(
            new CrudLayerDefinition("vo",               "VO.java",               "egovframework/let/{PKG}/service/"),
            new CrudLayerDefinition("mapper",           "Mapper.java",            "egovframework/let/{PKG}/service/impl/"),
            new CrudLayerDefinition("mapperXml",        "Mapper.xml",             "egovframework/let/{PKG}/service/impl/"),
            new CrudLayerDefinition("service",          "Service.java",           "egovframework/let/{PKG}/service/"),
            new CrudLayerDefinition("serviceImpl",      "ServiceImpl.java",       "egovframework/let/{PKG}/service/impl/"),
            new CrudLayerDefinition("controller",       "Controller.java",        "egovframework/let/{PKG}/web/"),
            new CrudLayerDefinition("controlleradvice", "ValidationHandler.java", "egovframework/let/{PKG}/web/"),
            new CrudLayerDefinition("jspList",          "List.jsp",               "jsp/{DOMAIN_LC}/"),
            new CrudLayerDefinition("jspDetail",        "Detail.jsp",             "jsp/{DOMAIN_LC}/"),
            new CrudLayerDefinition("jspRegist",        "Regist.jsp",             "jsp/{DOMAIN_LC}/"),
            new CrudLayerDefinition("jspUpdt",          "Updt.jsp",               "jsp/{DOMAIN_LC}/")
    );

    /**
     * vo / mapper / mapperXml / service 는 {Domain}Xxx,
     * 나머지(serviceImpl, controller, handler, jsp)는 Egov{Domain}Xxx.
     */
    public static String resolveFileName(String layerKey, String domain, String suffix) {
        return switch (layerKey) {
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
