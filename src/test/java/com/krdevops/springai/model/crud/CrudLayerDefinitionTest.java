package com.krdevops.springai.model.crud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrudLayerDefinitionTest {

    @Test
    void layers_total_11() {
        assertThat(CrudLayerDefinition.LAYERS).hasSize(11);
    }

    @Test
    void javaLayers_subPathTemplate_doesNotContainDomainFolder() {
        List<CrudLayerDefinition> javaLayers = CrudLayerDefinition.LAYERS.stream()
                .filter(l -> !l.layerKey().startsWith("jsp"))
                .toList();

        assertThat(javaLayers).isNotEmpty();
        assertThat(javaLayers)
                .allSatisfy(l -> assertThat(l.subPathTemplate())
                        .doesNotContain("{DOMAIN_LC}")
                        .contains("{PKG}"));
    }

    @Test
    void jspLayers_subPathTemplate_containsDomainFolder() {
        List<CrudLayerDefinition> jspLayers = CrudLayerDefinition.LAYERS.stream()
                .filter(l -> l.layerKey().startsWith("jsp"))
                .toList();

        assertThat(jspLayers).hasSize(4);
        assertThat(jspLayers)
                .allSatisfy(l -> assertThat(l.subPathTemplate())
                        .contains("{DOMAIN_LC}")
                        .doesNotContain("{PKG}"));
    }

    @Test
    void resolveFileName_voAndMapper_noPrefixEgov() {
        assertThat(CrudLayerDefinition.resolveFileName("vo",        "Employee", "VO.java"))
                .isEqualTo("EmployeeVO.java");
        assertThat(CrudLayerDefinition.resolveFileName("mapper",    "Employee", "Mapper.java"))
                .isEqualTo("EmployeeMapper.java");
        assertThat(CrudLayerDefinition.resolveFileName("mapperXml", "Employee", "Mapper.xml"))
                .isEqualTo("EmployeeMapper.xml");
        assertThat(CrudLayerDefinition.resolveFileName("service",   "Employee", "Service.java"))
                .isEqualTo("EmployeeService.java");
    }

    @Test
    void resolveFileName_controllerAndOthers_hasPrefixEgov() {
        assertThat(CrudLayerDefinition.resolveFileName("serviceImpl",      "Employee", "ServiceImpl.java"))
                .isEqualTo("EgovEmployeeServiceImpl.java");
        assertThat(CrudLayerDefinition.resolveFileName("controller",       "Employee", "Controller.java"))
                .isEqualTo("EgovEmployeeController.java");
        assertThat(CrudLayerDefinition.resolveFileName("controlleradvice", "Employee", "ValidationHandler.java"))
                .isEqualTo("EgovEmployeeValidationHandler.java");
        assertThat(CrudLayerDefinition.resolveFileName("jspList",          "Employee", "List.jsp"))
                .isEqualTo("EgovEmployeeList.jsp");
    }

    @Test
    void resolveSubPath_pkgPlaceholderReplaced() {
        CrudLayerDefinition vo = CrudLayerDefinition.LAYERS.stream()
                .filter(l -> l.layerKey().equals("vo"))
                .findFirst().orElseThrow();

        assertThat(vo.resolveSubPath("emp", "employer"))
                .isEqualTo("egovframework/let/emp/service/");
    }

    @Test
    void resolveSubPath_domainLcPlaceholderReplaced() {
        CrudLayerDefinition jspList = CrudLayerDefinition.LAYERS.stream()
                .filter(l -> l.layerKey().equals("jspList"))
                .findFirst().orElseThrow();

        assertThat(jspList.resolveSubPath("emp", "employer"))
                .isEqualTo("jsp/employer/");
    }

    @Test
    void layerKeys_expectedValues() {
        List<String> keys = CrudLayerDefinition.LAYERS.stream()
                .map(CrudLayerDefinition::layerKey)
                .toList();

        assertThat(keys).containsExactly(
                "vo", "mapper", "mapperXml", "service", "serviceImpl",
                "controller", "controlleradvice",
                "jspList", "jspDetail", "jspRegist", "jspUpdt");
    }
}
