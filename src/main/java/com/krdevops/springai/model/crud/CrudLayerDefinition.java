package com.krdevops.springai.model.crud;

import java.util.List;
import java.util.Set;

/**
 * eGovFrame CRUD 레이어의 파일명·경로 정의 (JSP: 11개, Thymeleaf: 16개).
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
    public static final String LAYOUT_HTML = "layoutHtml";
    public static final String LAYOUT_GNB_HTML = "layoutGnbHtml";
    public static final String LAYOUT_LNB_HTML = "layoutLnbHtml";
    public static final String LAYOUT_BREADCRUMB_HTML = "layoutBreadcrumbHtml";
    public static final String LAYOUT_FOOTER_HTML = "layoutFooterHtml";
    public static final String LAYOUT_GNB_MENU_VO = "layoutGnbMenuVo";
    public static final String LAYOUT_GNB_MENU_MAPPER = "layoutGnbMenuMapper";
    public static final String LAYOUT_GNB_MENU_MAPPER_XML = "layoutGnbMenuMapperXml";
    public static final String LAYOUT_GNB_MENU_INTERCEPTOR = "layoutGnbMenuInterceptor";

    /**
     * layoutMode=create 경로(CrudOrchestrationService 등)가 도메인 생성마다 함께 렌더링하는 layout HTML 5종.
     * GNB 메뉴 컴포넌트(VO/Mapper/MapperXml/Interceptor)는 프로젝트당 1회만 생성되는 별도 산출물이라
     * 의도적으로 여기에 포함하지 않는다 — {@link #GNB_MENU_COMPONENT_LAYERS} 참고.
     */
    public static final Set<String> LAYOUT_LAYER_KEYS = Set.of(
            LAYOUT_HTML,
            LAYOUT_GNB_HTML,
            LAYOUT_LNB_HTML,
            LAYOUT_BREADCRUMB_HTML,
            LAYOUT_FOOTER_HTML
    );

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
            new CrudLayerDefinition("layoutGnbHtml",     "layout/gnb.html",        "src/main/resources/templates/"),
            new CrudLayerDefinition("layoutLnbHtml",     "layout/lnb.html",        "src/main/resources/templates/"),
            new CrudLayerDefinition("layoutBreadcrumbHtml", "layout/breadcrumb.html", "src/main/resources/templates/"),
            new CrudLayerDefinition("layoutFooterHtml",  "layout/footer.html",     "src/main/resources/templates/"),
            new CrudLayerDefinition("thymeleafList",     "List.html",              "src/main/resources/templates/{DOMAIN_LC}/"),
            new CrudLayerDefinition("thymeleafDetail",   "Detail.html",            "src/main/resources/templates/{DOMAIN_LC}/"),
            new CrudLayerDefinition("thymeleafRegist",   "Regist.html",            "src/main/resources/templates/{DOMAIN_LC}/"),
            new CrudLayerDefinition("thymeleafUpdt",     "Updt.html",              "src/main/resources/templates/{DOMAIN_LC}/")
    );

    /**
     * GNB 동적 렌더링용 공용 메뉴 컴포넌트(VO/Mapper/MapperXml/Interceptor) — 프로젝트당 1회만 생성된다.
     * {@code ThymeleafLayoutTool.generateThymeleafLayout()} 전용이며,
     * 도메인마다 반복 호출되는 {@code forViewType()}/{@code layoutMode=create} 경로에는 절대 포함하지 않는다
     * (포함하면 도메인 생성마다 이 공용 파일을 다시 만들려다 충돌하거나, CrudTemplateModel 기반 렌더러가
     * 처리할 수 없는 layerKey를 만나 예외가 발생한다).
     *
     * <p>subPathTemplate의 {PKG}는 {@code COMMON_LAYERS}와 달리 "egovframework.let." 접두사를
     * 제거하지 않은 전체 packageName 치환값을 받는다 — 호출부(ThymeleafLayoutTool)에서
     * {@code packageName.replace(".", "/")}를 그대로 pkgSub으로 넘긴다.
     */
    public static final List<CrudLayerDefinition> GNB_MENU_COMPONENT_LAYERS = List.of(
            new CrudLayerDefinition(LAYOUT_GNB_MENU_VO,          "GnbMenuVO.java",              "src/main/java/{PKG}/cmm/vo/"),
            new CrudLayerDefinition(LAYOUT_GNB_MENU_MAPPER,      "GnbMenuMapper.java",          "src/main/java/{PKG}/cmm/service/"),
            new CrudLayerDefinition(LAYOUT_GNB_MENU_MAPPER_XML,  "GnbMenuMapper.xml",           "src/main/resources/egovframework/mapper/cmm/"),
            new CrudLayerDefinition(LAYOUT_GNB_MENU_INTERCEPTOR, "EgovGnbMenuInterceptor.java", "src/main/java/{PKG}/cmm/web/")
    );

    public static final List<CrudLayerDefinition> LAYERS = JSP_LAYERS;

    public static List<CrudLayerDefinition> forViewType(CrudViewType viewType) {
        return viewType == CrudViewType.THYMELEAF ? THYMELEAF_LAYERS : JSP_LAYERS;
    }

    public static boolean isLayoutLayer(String layerKey) {
        return LAYOUT_LAYER_KEYS.contains(layerKey);
    }

    public static List<CrudLayerDefinition> thymeleafLayoutLayers() {
        return THYMELEAF_LAYERS.stream()
                .filter(layer -> isLayoutLayer(layer.layerKey()))
                .toList();
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
            case "layoutGnbHtml"                         -> "layout/gnb.html";
            case "layoutLnbHtml"                         -> "layout/lnb.html";
            case "layoutBreadcrumbHtml"                  -> "layout/breadcrumb.html";
            case "layoutFooterHtml"                      -> "layout/footer.html";
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
