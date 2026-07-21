package com.krdevops.springai.model.masterdetail;

import com.krdevops.springai.model.crud.CrudViewType;

import java.util.List;
import java.util.Set;

/**
 * Master-detail CRUD layer file definitions.
 */
public record MasterDetailLayerDefinition(
        String layerKey,
        String fileNameRole,
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

    private static final List<MasterDetailLayerDefinition> COMMON_LAYERS = List.of(
            new MasterDetailLayerDefinition("masterVo",          "master", "VO.java",                "src/main/java/egovframework/let/{PKG}/service/"),
            new MasterDetailLayerDefinition("detailVo",          "detail", "VO.java",                "src/main/java/egovframework/let/{PKG}/service/"),
            new MasterDetailLayerDefinition("masterMapper",      "master", "Mapper.java",            "src/main/java/egovframework/let/{PKG}/service/impl/"),
            new MasterDetailLayerDefinition("detailMapper",      "detail", "Mapper.java",            "src/main/java/egovframework/let/{PKG}/service/impl/"),
            new MasterDetailLayerDefinition("masterMapperXml",   "master", "Mapper.xml",             "src/main/resources/egovframework/mapper/{DOMAIN_LC}/"),
            new MasterDetailLayerDefinition("detailMapperXml",   "detail", "Mapper.xml",             "src/main/resources/egovframework/mapper/{DOMAIN_LC}/"),
            new MasterDetailLayerDefinition("service",           "master", "Service.java",           "src/main/java/egovframework/let/{PKG}/service/"),
            new MasterDetailLayerDefinition("serviceImpl",       "master", "ServiceImpl.java",       "src/main/java/egovframework/let/{PKG}/service/impl/"),
            new MasterDetailLayerDefinition("controller",        "master", "Controller.java",        "src/main/java/egovframework/let/{PKG}/web/"),
            new MasterDetailLayerDefinition("validationHandler", "master", "ValidationHandler.java", "src/main/java/egovframework/let/{PKG}/web/")
    );

    public static final List<MasterDetailLayerDefinition> JSP_LAYERS = concat(
            COMMON_LAYERS,
            new MasterDetailLayerDefinition("jspList",   "master", "List.jsp",   "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new MasterDetailLayerDefinition("jspDetail", "master", "Detail.jsp", "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new MasterDetailLayerDefinition("jspRegist", "master", "Regist.jsp", "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/"),
            new MasterDetailLayerDefinition("jspUpdt",   "master", "Updt.jsp",   "src/main/webapp/WEB-INF/jsp/{DOMAIN_LC}/")
    );

    public static final List<MasterDetailLayerDefinition> THYMELEAF_LAYERS = concat(
            COMMON_LAYERS,
            new MasterDetailLayerDefinition("layoutHtml",      "layout", "layout/default.html", "src/main/resources/templates/"),
            new MasterDetailLayerDefinition("layoutGnbHtml",   "layout", "layout/gnb.html", "src/main/resources/templates/"),
            new MasterDetailLayerDefinition("layoutLnbHtml",   "layout", "layout/lnb.html", "src/main/resources/templates/"),
            new MasterDetailLayerDefinition("layoutBreadcrumbHtml", "layout", "layout/breadcrumb.html", "src/main/resources/templates/"),
            new MasterDetailLayerDefinition("layoutFooterHtml", "layout", "layout/footer.html", "src/main/resources/templates/"),
            new MasterDetailLayerDefinition("thymeleafList",   "master", "List.html",           "src/main/resources/templates/{DOMAIN_LC}/"),
            new MasterDetailLayerDefinition("thymeleafDetail", "master", "Detail.html",         "src/main/resources/templates/{DOMAIN_LC}/"),
            new MasterDetailLayerDefinition("thymeleafRegist", "master", "Regist.html",         "src/main/resources/templates/{DOMAIN_LC}/"),
            new MasterDetailLayerDefinition("thymeleafUpdt",   "master", "Updt.html",           "src/main/resources/templates/{DOMAIN_LC}/")
    );

    public static List<MasterDetailLayerDefinition> forViewType(CrudViewType viewType) {
        return viewType == CrudViewType.THYMELEAF ? THYMELEAF_LAYERS : JSP_LAYERS;
    }

    public static boolean isLayoutLayer(String layerKey) {
        return LAYOUT_LAYER_KEYS.contains(layerKey);
    }

    private static List<MasterDetailLayerDefinition> concat(
            List<MasterDetailLayerDefinition> common, MasterDetailLayerDefinition... views) {
        java.util.ArrayList<MasterDetailLayerDefinition> layers = new java.util.ArrayList<>(common);
        layers.addAll(List.of(views));
        return List.copyOf(layers);
    }

    public static String resolveFileName(MasterDetailLayerDefinition layer, String masterDomain, String detailDomain) {
        if ("layoutHtml".equals(layer.layerKey())) return "layout/default.html";
        if ("layoutGnbHtml".equals(layer.layerKey())) return "layout/gnb.html";
        if ("layoutLnbHtml".equals(layer.layerKey())) return "layout/lnb.html";
        if ("layoutBreadcrumbHtml".equals(layer.layerKey())) return "layout/breadcrumb.html";
        if ("layoutFooterHtml".equals(layer.layerKey())) return "layout/footer.html";
        String domain = "detail".equals(layer.fileNameRole()) ? detailDomain : masterDomain;
        return switch (layer.layerKey()) {
            case "masterVo", "detailVo", "masterMapper", "detailMapper", "masterMapperXml", "detailMapperXml", "service" ->
                    domain + layer.fileNameSuffix();
            default -> "Egov" + domain + layer.fileNameSuffix();
        };
    }

    public String resolveSubPath(String pkgSub, String domainLc) {
        return subPathTemplate
                .replace("{PKG}", pkgSub)
                .replace("{DOMAIN_LC}", domainLc);
    }
}
