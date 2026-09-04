package com.krdevops.springai.service.generation.layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTypeDetectorTest {

    private final ProjectTypeDetector detector = new ProjectTypeDetector();

    @Test
    void detectsWar_whenWebXmlExists(@TempDir Path root) throws Exception {
        Path webXml = root.resolve("src/main/webapp/WEB-INF/web.xml");
        Files.createDirectories(webXml.getParent());
        Files.writeString(webXml, "<web-app/>");

        assertThat(detector.detect(root)).isEqualTo(ProjectTypeDetector.ProjectType.WAR);
    }

    @Test
    void detectsBoot_whenApplicationYmlExists(@TempDir Path root) throws Exception {
        Path yml = root.resolve("src/main/resources/application.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "spring:\n");

        assertThat(detector.detect(root)).isEqualTo(ProjectTypeDetector.ProjectType.BOOT);
    }

    @Test
    void detectsBoot_whenApplicationPropertiesExists(@TempDir Path root) throws Exception {
        Path props = root.resolve("src/main/resources/application.properties");
        Files.createDirectories(props.getParent());
        Files.writeString(props, "server.port=8080");

        assertThat(detector.detect(root)).isEqualTo(ProjectTypeDetector.ProjectType.BOOT);
    }

    @Test
    void warWins_whenBothMarkersPresent(@TempDir Path root) throws Exception {
        Path webXml = root.resolve("src/main/webapp/WEB-INF/web.xml");
        Files.createDirectories(webXml.getParent());
        Files.writeString(webXml, "<web-app/>");
        Path yml = root.resolve("src/main/resources/application.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "spring:\n");

        assertThat(detector.detect(root)).isEqualTo(ProjectTypeDetector.ProjectType.WAR);
    }

    @Test
    void detectsUnknown_whenNoMarker(@TempDir Path root) {
        assertThat(detector.detect(root)).isEqualTo(ProjectTypeDetector.ProjectType.UNKNOWN);
    }

    @Test
    void detectsUnknown_whenNull() {
        assertThat(detector.detect(null)).isEqualTo(ProjectTypeDetector.ProjectType.UNKNOWN);
    }
}
