package com.krdevops.springai.service.generation.layout;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 생성 대상 프로젝트 루트 구조를 보고 WAR / Boot 를 판정한다.
 *
 * <p>{@code generateThymeleafLayout} 는 파라미터로 projectType 을 받지 않는다 —
 * 호출부가 잘못 지정하면 산출물이 어긋나므로, 디스크에 이미 만들어진 구조로만 판정한다.
 *
 * <pre>
 * WAR  : {root}/src/main/webapp/WEB-INF/web.xml 존재
 * BOOT : 위가 없고 application.yml / application.yaml / application.properties 존재
 * UNKNOWN : 둘 다 아님 → 호출부는 기존 WAR 경로로 폴백하고 경고를 남긴다
 * </pre>
 */
@Slf4j
@Component
public class ProjectTypeDetector {

    public enum ProjectType { WAR, BOOT, UNKNOWN }

    private static final String WEB_XML = "src/main/webapp/WEB-INF/web.xml";
    private static final String[] BOOT_MARKERS = {
            "src/main/resources/application.yml",
            "src/main/resources/application.yaml",
            "src/main/resources/application.properties"
    };

    public ProjectType detect(Path projectRoot) {
        if (projectRoot == null) {
            return ProjectType.UNKNOWN;
        }
        if (Files.isRegularFile(projectRoot.resolve(WEB_XML))) {
            return ProjectType.WAR;
        }
        for (String marker : BOOT_MARKERS) {
            if (Files.isRegularFile(projectRoot.resolve(marker))) {
                return ProjectType.BOOT;
            }
        }
        log.info("[project-type] WAR/Boot 구조 마커를 찾지 못함 — UNKNOWN: {}", projectRoot);
        return ProjectType.UNKNOWN;
    }
}
