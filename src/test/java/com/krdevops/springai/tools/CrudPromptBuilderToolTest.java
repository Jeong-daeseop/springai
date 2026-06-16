package com.krdevops.springai.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CrudPromptBuilderToolTest {

    @Test
    void layerPaths_javaLayersFollowPackagePathWithoutDomainFolder() throws Exception {
        Map<String, String> paths = layerPaths();

        assertThat(paths)
            .containsEntry("vo", "egovframework/let/{PKG}/service/")
            .containsEntry("mapper", "egovframework/let/{PKG}/service/impl/")
            .containsEntry("mapperXml", "egovframework/let/{PKG}/service/impl/")
            .containsEntry("service", "egovframework/let/{PKG}/service/")
            .containsEntry("serviceImpl", "egovframework/let/{PKG}/service/impl/")
            .containsEntry("controller", "egovframework/let/{PKG}/web/")
            .containsEntry("controlleradvice", "egovframework/let/{PKG}/web/");

        assertThat(paths.entrySet())
            .filteredOn(entry -> entry.getKey().startsWith("jsp"))
            .isNotEmpty();
        assertThat(paths.entrySet())
            .filteredOn(entry -> !entry.getKey().startsWith("jsp"))
            .allMatch(entry -> !entry.getValue().contains("{DOMAIN_LC}"));
    }

    @Test
    void layerPaths_jspLayersKeepDomainFolder() throws Exception {
        Map<String, String> paths = layerPaths();

        assertThat(paths)
            .containsEntry("jspList", "jsp/{DOMAIN_LC}/")
            .containsEntry("jspDetail", "jsp/{DOMAIN_LC}/")
            .containsEntry("jspRegist", "jsp/{DOMAIN_LC}/")
            .containsEntry("jspUpdt", "jsp/{DOMAIN_LC}/");
    }

    private static Map<String, String> layerPaths() throws Exception {
        Field field = CrudPromptBuilderTool.class.getDeclaredField("LAYERS");
        field.setAccessible(true);
        String[][] layers = (String[][]) field.get(null);
        return Arrays.stream(layers)
            .collect(Collectors.toMap(layer -> layer[0], layer -> layer[2]));
    }
}
