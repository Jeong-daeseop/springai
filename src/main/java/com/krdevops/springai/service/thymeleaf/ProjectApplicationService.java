package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ProjectApplicationResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * I-7: 생성된 Thymeleaf 화면을 실제 eGovFrame 프로젝트에 적용.
 * WAR/Boot 프로젝트 구조에 따라 배포 경로 결정 및 파일 배치.
 */
@Service
public class ProjectApplicationService {

    private static final String WAR_TEMPLATE_PATH = "src/main/webapp/WEB-INF/templates";
    private static final String BOOT_TEMPLATE_PATH = "src/main/resources/templates";

    public ProjectApplicationResult applyToProject(
            Path projectRoot,
            String generatedThymeleafBasePath,
            List<String> screenDeployRequests) {
        String applicationId = "app-" + UUID.randomUUID();

        // 프로젝트 타입 감지 (WAR vs Boot)
        ProjectType projectType = detectProjectType(projectRoot);
        String deployTargetPath = (projectType == ProjectType.WAR)
                ? WAR_TEMPLATE_PATH
                : BOOT_TEMPLATE_PATH;

        List<ProjectApplicationResult.DeployedScreenInfo> deployedScreens = new ArrayList<>();
        int deployedCount = 0;
        int failureCount = 0;

        for (String screenRequest : screenDeployRequests) {
            try {
                Path sourceFile = Path.of(generatedThymeleafBasePath).resolve(screenRequest);
                if (!Files.exists(sourceFile)) {
                    failureCount++;
                    deployedScreens.add(new ProjectApplicationResult.DeployedScreenInfo(
                            screenRequest, screenRequest, deployTargetPath + "/" + screenRequest,
                            "", false, "소스 파일 없음: " + sourceFile));
                    continue;
                }

                Path targetDir = projectRoot.resolve(deployTargetPath);
                Files.createDirectories(targetDir);

                Path targetFile = targetDir.resolve(screenRequest);
                Files.createDirectories(targetFile.getParent());
                Files.copy(sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                deployedCount++;
                deployedScreens.add(new ProjectApplicationResult.DeployedScreenInfo(
                        screenRequest, screenRequest, targetFile.toString(),
                        inferControllerRoute(screenRequest), true, null));
            } catch (IOException exception) {
                failureCount++;
                deployedScreens.add(new ProjectApplicationResult.DeployedScreenInfo(
                        screenRequest, screenRequest, deployTargetPath + "/" + screenRequest,
                        "", false, exception.getMessage()));
            }
        }

        ProjectApplicationResult.DeploymentStatus status = failureCount == 0
                ? ProjectApplicationResult.DeploymentStatus.SUCCESS
                : (deployedCount > 0
                ? ProjectApplicationResult.DeploymentStatus.PARTIAL_SUCCESS
                : ProjectApplicationResult.DeploymentStatus.FAILED);

        return new ProjectApplicationResult(
                applicationId,
                projectRoot.toString(),
                deployedCount,
                failureCount,
                deployedScreens,
                status,
                status == ProjectApplicationResult.DeploymentStatus.SUCCESS
                        ? deployedCount + "개 화면 배포 완료"
                        : failureCount + "개 배포 실패",
                Instant.now()
        );
    }

    public List<String> validateDeployment(ProjectApplicationResult result) {
        List<String> issues = new ArrayList<>();

        if (result.filesDeployed() == 0) {
            issues.add("배포된 파일이 없습니다");
        }

        if (result.deploymentFailures() > 0) {
            issues.add(result.deploymentFailures() + "개 파일 배포 실패");
        }

        for (ProjectApplicationResult.DeployedScreenInfo screen : result.deployedScreens()) {
            if (!screen.deployed()) {
                issues.add("배포 실패: " + screen.screenId() + " (" + screen.deployError() + ")");
            }

            Path deployedPath = Path.of(screen.thymeleafDeployPath());
            if (!Files.exists(deployedPath)) {
                issues.add("배포된 파일 없음: " + deployedPath);
            }
        }

        return issues;
    }

    private ProjectType detectProjectType(Path projectRoot) {
        if (Files.exists(projectRoot.resolve("pom.xml"))) {
            // pom.xml에서 WAR vs JAR 구분
            try {
                String pomContent = Files.readString(projectRoot.resolve("pom.xml"));
                if (pomContent.contains("<packaging>war</packaging>")) {
                    return ProjectType.WAR;
                }
            } catch (IOException ignored) {
            }
        }

        if (Files.exists(projectRoot.resolve("build.gradle"))) {
            return ProjectType.BOOT;
        }

        // 기본값: WAR (eGovFrame 표준)
        return ProjectType.WAR;
    }

    private String inferControllerRoute(String screenPath) {
        String name = screenPath.replace(".html", "").replaceAll("^.*/", "");
        if (name.toLowerCase().contains("list")) {
            return "/" + name.toLowerCase() + ".do";
        }
        if (name.toLowerCase().contains("regist")) {
            return "/" + name.toLowerCase().replace("regist", "create") + ".do";
        }
        if (name.toLowerCase().contains("detail")) {
            return "/" + name.toLowerCase() + ".do";
        }
        return "/" + name.toLowerCase() + ".do";
    }

    private enum ProjectType {
        WAR, BOOT
    }
}
