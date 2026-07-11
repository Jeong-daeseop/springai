package com.krdevops.springai.tools;

import com.krdevops.springai.service.ProjectInitializrService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectInitializrTool {

    private final ProjectInitializrService projectInitializrService;

    @Tool(description = """
            ⚠️ 이 Tool이 outputPath 경로에 프로젝트 파일을 직접 생성합니다.
            Desktop Commander, Bash, 기타 파일 생성 도구를 사용하지 마세요.
            eGovFrame 표준 구조(build.gradle/pom.xml, application.yml, Application.java,
            context-*.xml 등)는 이 Tool만 올바르게 생성합니다.

            Spring Initializr처럼 eGovFrame 신규 프로젝트 골격을 한 번에 생성합니다.

            [projectType]
              war  — 전통 eGovFrame WAR 배포 방식
                     web.xml + root-context.xml + servlet-context.xml + context-*.xml 구성
                     외부 Tomcat에 WAR 배포
              boot — Spring Boot 기반 eGovFrame
                     application.yml + @SpringBootApplication 구성
                     내장 서버(jar) 실행

            [egovVersion]
              4.3    — eGovFrame 4.3.0 / Spring Boot 2.7.18 / Spring 5.3.37
                       Spring Security 5.8.13 / Spring Batch 4.3.10 / Java 11 / javax.servlet 4.0
              latest — eGovFrame 5.0.0 / Spring Boot 3.5.6 / Spring 6.2.11
                       Spring Security 6.5.5 / Spring Batch 5.2.3 / Java 17 / Jakarta EE 10

            [지원 조합]
              war  + 4.3    → Spring 5.3.37 / Java 11 / javax.servlet  / XML 설정
              war  + latest → Spring 6.2.11 / Java 17 / Jakarta EE 10  / XML 설정
              boot + 4.3    → Spring Boot 2.7.18 / Java 11 / mybatis-spring-boot-starter 2.x
              boot + latest → Spring Boot 3.5.6  / Java 17 / mybatis-spring-boot-starter 3.x

            [생성 파일]
              공통 : 표준 디렉터리 구조, pom.xml 또는 build.gradle, .gitignore
              war  : root-context.xml, servlet-context.xml, context-common/datasource/transaction.xml,
                     web.xml, log4j2.xml, index.jsp,
                     resources/css/styles.css, resources/css/_ds_bundle.css, resources/js/krds.min.js
              boot : application.yml, logback-spring.xml,
                     static/resources/css/styles.css, static/resources/css/_ds_bundle.css,
                     static/resources/js/krds.min.js,
                     {Domain}Application.java, {Domain}ApplicationTests.java

            [파라미터]
              projectName : 프로젝트 폴더명       (예: egov-myproject)
              groupId     : Maven groupId          (예: kr.go.myorg)
              artifactId  : Maven artifactId       (예: myproject)
              packageName : 기본 Java 패키지       (예: egovframework.let.myproject)
              buildTool   : maven 또는 gradle
              projectType : war 또는 boot
              egovVersion : 4.3 또는 5.0 또는 latest (5.0 = latest 동일)
              outputPath  : 생성 상위 경로         (예: /Users/user/Desktop)
              viewType    : 화면 기술              (선택, 기본값 "jsp")
                            - "jsp"       : MainController + WEB-INF/jsp/egovframework/main/main.jsp 생성
                            - "thymeleaf" : MainController + templates/egovframework/main/main.html
                                            + layout 5종 + index.html + Thymeleaf ViewResolver 생성

            ⚠️ 사용자가 "프로젝트 생성", "새 프로젝트 만들어줘", "프로젝트 초기화" 요청 시
               반드시 이 Tool을 직접 호출하세요. Desktop Commander나 Bash로 대체하지 마세요.
            이 Tool은 프로젝트 골격 생성 전용입니다. CRUD/게시판 소스 생성은 자동으로 이어서 호출하지 마세요.
            CRUD/게시판 생성은 사용자가 별도로 요청했을 때만 해당 Tool을 사용하세요.
            projectType 또는 egovVersion 미입력 시 사용자에게 물어보세요.
            """)
    public String initializeProject(String projectName, String groupId, String artifactId,
                                    String packageName, String buildTool,
                                    String projectType, String egovVersion, String outputPath,
                                    @Nullable String viewType) {
        return projectInitializrService.initializeProject(
            projectName, groupId, artifactId, packageName, buildTool,
            projectType, egovVersion, outputPath, viewType);
    }

    @Tool(description = """
            eGovFrame 설정 파일 템플릿을 반환합니다. (추가_권장_항목 12번)
            신규 프로젝트 구성 또는 기존 프로젝트 설정 누락 항목 보완 시 사용합니다.

            [configType 목록]
              contextCommon      — context-common.xml
                                   컴포넌트 스캔, SqlSessionFactory, MapperScanner 설정
              contextDatasource  — context-datasource.xml
                                   HikariCP DataSource 설정 (DB 연결 정보 포함)
              contextTransaction — context-transaction.xml
                                   DataSourceTransactionManager + AOP 트랜잭션 설정
              dispatcherServlet  — servlet-context.xml (DispatcherServlet 웹 계층 설정)
                                   Spring MVC 컨트롤러 스캔, ViewResolver, 파일 업로드 설정
              webXml             — web.xml (Jakarta EE 6.0 기준)
                                   ContextLoaderListener, DispatcherServlet, 인코딩 필터 설정
              logback            — logback-spring.xml (Spring Boot 전용)
                                   콘솔 + 파일 롤링 로그 설정
              log4j2             — log4j2.xml (WAR 전통 방식 전용)
                                   콘솔 + 파일 롤링 로그 설정
              applicationYml     — application.yml (Spring Boot 전용)
                                   datasource / mybatis / server / logging 설정
                                   local / prod 프로파일 분리 포함

            [packageName]
              contextCommon, dispatcherServlet, applicationYml 에서 패키지명으로 사용됩니다.
              생략 시 egovframework.let.sample 이 적용됩니다.

            기존 프로젝트에서 특정 설정 파일만 필요할 때 또는
            initializeProject() 이후 개별 설정 파일을 수정·보완할 때 사용하세요.
            """)
    public String getConfigTemplate(String configType, String packageName) {
        return projectInitializrService.getConfigTemplate(configType, packageName);
    }
}
