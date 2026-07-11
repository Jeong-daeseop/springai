package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;
import org.springframework.stereotype.Component;

import static com.krdevops.springai.service.initializr.FilePlanFactory.*;

@Component
public class WarPomBuilder {

    public String build(ProjectSpec s) {
        String projectName      = s.projectName();
        String egovVer          = s.cap().egovParent() ? EGOV_50 : EGOV_43;
        String javaVer          = s.cap().java17()          ? JAVA_17          : JAVA_11;
        String springVer        = s.cap().spring6()         ? SPRING_6         : SPRING_5;
        String mybatisSpringVer = s.cap().myBatisSpring3()  ? MYBATIS_SPRING_3 : MYBATIS_SPRING_2;
        boolean useParent       = s.cap().egovParent();

        String parentBlock = useParent
            ? """

    <parent>
        <groupId>%s</groupId>
        <artifactId>%s</artifactId>
        <version>%s</version>
        <relativePath/>
    </parent>
""".formatted(EGOV_WAR_PARENT_GROUP, EGOV_WAR_PARENT_ARTIFACT, egovVer)
            : "";

        String versionProps = useParent
            ? """
        <java.version>%s</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <egov.version>%s</egov.version>
        <mybatis.version>%s</mybatis.version>
        <junit.jupiter.version>5.12.1</junit.jupiter.version>
        <lombok.version>1.18.46</lombok.version>""".formatted(javaVer, egovVer, MYBATIS_35)
            : """
        <java.version>%s</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <egov.version>%s</egov.version>
        <spring.version>%s</spring.version>
        <mybatis.version>%s</mybatis.version>
        <junit.jupiter.version>5.10.2</junit.jupiter.version>
        <lombok.version>1.18.46</lombok.version>""".formatted(javaVer, egovVer, springVer, MYBATIS_35);

        String egovDeps = useParent
            ? """
                <!-- eGovFrame 5.0 — version managed by egovframe-web-config-parent -->
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-ptl-mvc</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-psl-dataaccess</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-fdl-cmmn</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-fdl-property</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-fdl-idgnr</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-fdl-logging</artifactId>
                    <exclusions>
                        <exclusion>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-slf4j-impl</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j-slf4j2-impl</artifactId>
                    <version>2.25.2</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-fdl-security</artifactId>
                </dependency>"""
            : """
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.ptl.mvc</artifactId>
                    <version>${egov.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.psl.dataaccess</artifactId>
                    <version>${egov.version}</version>
                    <exclusions>
                        <!-- JsonbHttpMessageConverter 초기화 실패 방지 -->
                        <exclusion>
                            <groupId>javax</groupId>
                            <artifactId>javaee-api</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.fdl.cmmn</artifactId>
                    <version>${egov.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.fdl.property</artifactId>
                    <version>${egov.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.fdl.idgnr</artifactId>
                    <version>${egov.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.fdl.logging</artifactId>
                    <version>${egov.version}</version>
                    <exclusions>
                        <exclusion>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-slf4j-impl</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j-slf4j2-impl</artifactId>
                    <version>2.25.2</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.fdl.security</artifactId>
                    <version>${egov.version}</version>
                </dependency>""";

        String mybatisBlock = """
        <!-- MyBatis -->
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis</artifactId>
            <version>${mybatis.version}</version>
        </dependency>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis-spring</artifactId>
            <version>%s</version>
        </dependency>""".formatted(mybatisSpringVer);

        String servletDep = useParent
            ? """
                <!-- Servlet/JSP — servlet-api version managed by egovframe-web-config-parent -->
                <dependency>
                    <groupId>jakarta.servlet</groupId>
                    <artifactId>jakarta.servlet-api</artifactId>
                    <scope>provided</scope>
                </dependency>
                <dependency>
                    <groupId>jakarta.servlet.jsp</groupId>
                    <artifactId>jakarta.servlet.jsp-api</artifactId>
                    <scope>provided</scope>
                </dependency>
                <dependency>
                    <groupId>jakarta.servlet.jsp.jstl</groupId>
                    <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
                    <version>3.0.0</version>
                </dependency>
                <dependency>
                    <groupId>org.glassfish.web</groupId>
                    <artifactId>jakarta.servlet.jsp.jstl</artifactId>
                    <version>3.0.1</version>
                </dependency>"""
            : """
                <dependency>
                    <groupId>javax.servlet</groupId>
                    <artifactId>javax.servlet-api</artifactId>
                    <version>4.0.1</version>
                    <scope>provided</scope>
                </dependency>
                <dependency>
                    <groupId>javax.servlet.jsp</groupId>
                    <artifactId>javax.servlet.jsp-api</artifactId>
                    <version>2.3.3</version>
                    <scope>provided</scope>
                </dependency>
                <dependency>
                    <groupId>javax.servlet</groupId>
                    <artifactId>jstl</artifactId>
                    <version>1.2</version>
                </dependency>
                <!-- CommonsMultipartResolver 의존성 (Spring 5 WAR 방식) -->
                <dependency>
                    <groupId>commons-fileupload</groupId>
                    <artifactId>commons-fileupload</artifactId>
                    <version>1.5</version>
                </dependency>""";

        String validationDep = useParent
            ? """
                <!-- Validation — jakarta.validation-api version managed by egovframe-web-config-parent -->
                <dependency>
                    <groupId>jakarta.validation</groupId>
                    <artifactId>jakarta.validation-api</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.hibernate.validator</groupId>
                    <artifactId>hibernate-validator</artifactId>
                    <version>8.0.1.Final</version>
                </dependency>
                <dependency>
                    <groupId>org.glassfish</groupId>
                    <artifactId>jakarta.el</artifactId>
                    <version>4.0.2</version>
                </dependency>"""
            : """
                <dependency>
                    <groupId>javax.validation</groupId>
                    <artifactId>validation-api</artifactId>
                    <version>2.0.1.Final</version>
                </dependency>
                <dependency>
                    <groupId>org.hibernate.validator</groupId>
                    <artifactId>hibernate-validator</artifactId>
                    <version>6.2.5.Final</version>
                </dependency>
                <dependency>
                    <groupId>org.glassfish</groupId>
                    <artifactId>jakarta.el</artifactId>
                    <version>3.0.4</version>
                </dependency>""";

        String thymeleafDep = !s.thymeleaf() ? "" : useParent
            ? """

        <!-- Thymeleaf -->
        <dependency>
            <groupId>org.thymeleaf</groupId>
            <artifactId>thymeleaf-spring6</artifactId>
            <version>3.1.3.RELEASE</version>
        </dependency>
        <dependency>
            <groupId>nz.net.ultraq.thymeleaf</groupId>
            <artifactId>thymeleaf-layout-dialect</artifactId>
            <version>3.4.0</version>
        </dependency>"""
            : """

        <!-- Thymeleaf -->
        <dependency>
            <groupId>org.thymeleaf</groupId>
            <artifactId>thymeleaf-spring5</artifactId>
            <version>3.0.15.RELEASE</version>
        </dependency>
        <dependency>
            <groupId>nz.net.ultraq.thymeleaf</groupId>
            <artifactId>thymeleaf-layout-dialect</artifactId>
            <version>3.1.0</version>
        </dependency>""";

        String testBlock = useParent
            ? """
        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.jupiter.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <scope>test</scope>
        </dependency>"""
            : """
        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.jupiter.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>""";

        String buildSection = """

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>${java.version}</release>
                    <encoding>UTF-8</encoding>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
            </plugin>
        </plugins>
    </build>
""";

        return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
%s
    <groupId>%s</groupId>
    <artifactId>%s</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>war</packaging>
    <name>%s</name>

    <!-- ── 버전 정의 ── -->
    <properties>
%s
    </properties>

    <repositories>
        <repository>
            <id>egovframe</id>
            <url>https://maven.egovframe.go.kr/maven/</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- eGovFrame 핵심 -->
%s

%s

        <!-- DB (버전 직접 명시 — parent 관리 여부 미확인) -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>8.4.0</version>
        </dependency>
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
            <version>5.1.0</version>
        </dependency>

        <!-- Servlet / JSP -->
%s

        <!-- Validation -->
%s
%s

        <!-- Lombok (1.18.46 이상 필수 — JDK 21+ TypeTag::UNKNOWN 오류 해결) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.46</version>
            <scope>provided</scope>
        </dependency>

%s
    </dependencies>
%s
</project>
""".formatted(parentBlock, s.groupId(), s.artifactId(), projectName,
              versionProps, egovDeps, mybatisBlock,
              servletDep, validationDep, thymeleafDep, testBlock, buildSection);
    }
}
