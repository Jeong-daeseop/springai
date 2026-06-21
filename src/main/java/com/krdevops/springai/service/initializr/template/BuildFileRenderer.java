package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;

/**
 * 버전 조건 분기가 있는 빌드·배포 파일 렌더러 인터페이스.
 * pom.xml, build.gradle, servlet-context.xml, web.xml 을 담당합니다.
 */
public interface BuildFileRenderer {
    String warPom(ProjectSpec s);
    String bootPom(ProjectSpec s);
    String warBuildGradle(ProjectSpec s);
    String bootBuildGradle(ProjectSpec s);
    String dispatcherServlet(ProjectSpec s);
    String webXml(ProjectSpec s);
}
