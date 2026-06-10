package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;

/**
 * 정적 구조 템플릿(.tpl 파일 기반) 렌더러 인터페이스.
 * 조건 분기가 없는 설정 파일·소스 파일을 담당합니다.
 */
public interface StaticTemplateRenderer {
    String contextCommon(ProjectSpec s);
    String contextDatasource();
    String contextTransaction();
    String applicationYml(ProjectSpec s);
    String logback(ProjectSpec s);
    String log4j2(ProjectSpec s);
    String gitignore(ProjectSpec s);
    String indexJsp();
    String bootMain(ProjectSpec s);
    String bootTest(ProjectSpec s);
}
