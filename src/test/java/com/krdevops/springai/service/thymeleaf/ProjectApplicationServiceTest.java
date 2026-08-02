package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ProjectApplicationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I-7: 생성된 Thymeleaf 화면을 실제 프로젝트에 적용하고 배포하는 테스트.
 */
class ProjectApplicationServiceTest {

    private ProjectApplicationService applicationService;

    @TempDir
    Path tempProjectRoot;

    @TempDir
    Path tempGeneratedBasePath;

    @BeforeEach
    void setUp() {
        applicationService = new ProjectApplicationService();
    }

    @Test
    void detectsWarProjectStructure() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF"));
        Files.writeString(tempProjectRoot.resolve("pom.xml"),
                "<project><packaging>war</packaging></project>");

        ProjectApplicationResult result = applicationService.applyToProject(
                tempProjectRoot, tempGeneratedBasePath.toString(), List.of());

        assertThat(result.projectPath()).isEqualTo(tempProjectRoot.toString());
    }

    @Test
    void detectsBootProjectStructure() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/resources"));
        Files.writeString(tempProjectRoot.resolve("build.gradle"), "plugins {}");

        ProjectApplicationResult result = applicationService.applyToProject(
                tempProjectRoot, tempGeneratedBasePath.toString(), List.of());

        assertThat(result.projectPath()).isEqualTo(tempProjectRoot.toString());
    }

    @Test
    void deploysThymeleafScreensToWarProject() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF"));
        Files.writeString(tempProjectRoot.resolve("pom.xml"),
                "<project><packaging>war</packaging></project>");

        Files.createDirectories(tempGeneratedBasePath.resolve("screens"));
        Files.writeString(tempGeneratedBasePath.resolve("screens/EgovEmployerList.html"),
                "<html><body>List</body></html>");
        Files.writeString(tempGeneratedBasePath.resolve("screens/EgovEmployerDetail.html"),
                "<html><body>Detail</body></html>");

        ProjectApplicationResult result = applicationService.applyToProject(
                tempProjectRoot,
                tempGeneratedBasePath.toString(),
                List.of("screens/EgovEmployerList.html", "screens/EgovEmployerDetail.html"));

        assertThat(result.filesDeployed()).isEqualTo(2);
        assertThat(result.deploymentFailures()).isEqualTo(0);
        assertThat(result.status()).isEqualTo(ProjectApplicationResult.DeploymentStatus.SUCCESS);

        Path deployedList = tempProjectRoot.resolve("src/main/webapp/WEB-INF/templates/screens/EgovEmployerList.html");
        Path deployedDetail = tempProjectRoot.resolve("src/main/webapp/WEB-INF/templates/screens/EgovEmployerDetail.html");
        assertThat(Files.exists(deployedList)).isTrue();
        assertThat(Files.exists(deployedDetail)).isTrue();
        assertThat(Files.readString(deployedList)).contains("List");
        assertThat(Files.readString(deployedDetail)).contains("Detail");
    }

    @Test
    void deploysThymeleafScreensToBootProject() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/resources"));
        Files.writeString(tempProjectRoot.resolve("build.gradle"), "plugins {}");

        Files.createDirectories(tempGeneratedBasePath.resolve("templates"));
        Files.writeString(tempGeneratedBasePath.resolve("templates/EgovEmployerList.html"),
                "<html><body>Boot List</body></html>");

        ProjectApplicationResult result = applicationService.applyToProject(
                tempProjectRoot,
                tempGeneratedBasePath.toString(),
                List.of("templates/EgovEmployerList.html"));

        assertThat(result.filesDeployed()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(ProjectApplicationResult.DeploymentStatus.SUCCESS);

        Path deployedList = tempProjectRoot.resolve("src/main/resources/templates/templates/EgovEmployerList.html");
        assertThat(Files.exists(deployedList)).isTrue();
        assertThat(Files.readString(deployedList)).contains("Boot List");
    }

    @Test
    void handlesPartialDeploymentFailure() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF"));
        Files.writeString(tempProjectRoot.resolve("pom.xml"),
                "<project><packaging>war</packaging></project>");

        Files.createDirectories(tempGeneratedBasePath.resolve("screens"));
        Files.writeString(tempGeneratedBasePath.resolve("screens/Success.html"),
                "<html>Success</html>");

        ProjectApplicationResult result = applicationService.applyToProject(
                tempProjectRoot,
                tempGeneratedBasePath.toString(),
                List.of("screens/Success.html", "screens/NotFound.html"));

        assertThat(result.filesDeployed()).isEqualTo(1);
        assertThat(result.deploymentFailures()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(ProjectApplicationResult.DeploymentStatus.PARTIAL_SUCCESS);
    }

    @Test
    void validatesDeploymentResults() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF"));
        Files.writeString(tempProjectRoot.resolve("pom.xml"),
                "<project><packaging>war</packaging></project>");

        Files.createDirectories(tempGeneratedBasePath.resolve("screens"));
        Files.writeString(tempGeneratedBasePath.resolve("screens/Test.html"), "<html>Test</html>");

        ProjectApplicationResult result = applicationService.applyToProject(
                tempProjectRoot,
                tempGeneratedBasePath.toString(),
                List.of("screens/Test.html"));

        List<String> issues = applicationService.validateDeployment(result);

        assertThat(issues).isEmpty();
    }

    @Test
    void identifiesDeploymentIssues() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF"));
        Files.writeString(tempProjectRoot.resolve("pom.xml"),
                "<project><packaging>war</packaging></project>");

        ProjectApplicationResult result = applicationService.applyToProject(
                tempProjectRoot,
                tempGeneratedBasePath.toString(),
                List.of("nonexistent/file.html"));

        List<String> issues = applicationService.validateDeployment(result);

        assertThat(issues).isNotEmpty();
        assertThat(issues).anyMatch(issue -> issue.contains("배포 실패"));
    }

    @Test
    void createsDirectoryStructureAutomatically() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF"));
        Files.writeString(tempProjectRoot.resolve("pom.xml"),
                "<project><packaging>war</packaging></project>");

        Files.createDirectories(tempGeneratedBasePath.resolve("deep/nested/screens"));
        Files.writeString(tempGeneratedBasePath.resolve("deep/nested/screens/Nested.html"),
                "<html>Nested</html>");

        ProjectApplicationResult result = applicationService.applyToProject(
                tempProjectRoot,
                tempGeneratedBasePath.toString(),
                List.of("deep/nested/screens/Nested.html"));

        assertThat(result.filesDeployed()).isEqualTo(1);
        Path deployedNestedPath = tempProjectRoot.resolve(
                "src/main/webapp/WEB-INF/templates/deep/nested/screens/Nested.html");
        assertThat(Files.exists(deployedNestedPath)).isTrue();
    }
}
