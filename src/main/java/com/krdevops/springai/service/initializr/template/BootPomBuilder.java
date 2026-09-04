package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;
import org.springframework.stereotype.Component;

import static com.krdevops.springai.service.initializr.FilePlanFactory.*;

@Component
public class BootPomBuilder {

    public String build(ProjectSpec s) {
        String projectName = s.projectName();
        String sbVer     = s.cap().boot3()              ? SPRING_BOOT_3              : SPRING_BOOT_2;
        String javaVer   = s.cap().java17()             ? JAVA_17                    : JAVA_11;
        String mbsbVer   = s.cap().boot3()              ? MYBATIS_SB3                : MYBATIS_SB2;
        String egovVer   = s.cap().egovParent()         ? EGOV_50                    : EGOV_43;
        String fdlCmmnId = s.cap().hyphenArtifactId()   ? "egovframe-rte-fdl-cmmn"  : "org.egovframe.rte.fdl.cmmn";

        boolean useParent = s.cap().egovParent();

        String parentGroupId    = useParent ? EGOV_BOOT_PARENT_GROUP    : "org.springframework.boot";
        String parentArtifactId = useParent ? EGOV_BOOT_PARENT_ARTIFACT : "spring-boot-starter-parent";
        String parentVersion    = useParent ? egovVer                   : sbVer;

        String egovVersionProp = useParent ? ""
                : "        <egov.version>" + egovVer + "</egov.version>\n";

        String egovRteVersion = useParent ? ""
                : "            <version>${egov.version}</version>\n";

        String thymeleafDeps = !s.thymeleaf() ? "" : """

        <!-- Thymeleaf (viewType=thymeleaf) — ViewResolver/LayoutDialect는 Boot auto-configuration이 구성 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>nz.net.ultraq.thymeleaf</groupId>
            <artifactId>thymeleaf-layout-dialect</artifactId>
        </dependency>
""";

        return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>%s</groupId>
        <artifactId>%s</artifactId>
        <version>%s</version>
        <relativePath/>
    </parent>

    <groupId>%s</groupId>
    <artifactId>%s</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>%s</name>

    <properties>
        <java.version>%s</java.version>
%s    </properties>

    <repositories>
        <repository>
            <id>egovframe</id>
            <url>https://maven.egovframe.go.kr/maven/</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MyBatis Spring Boot Starter -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>%s</version>
        </dependency>
%s

        <!-- eGovFrame 공통 (fdl.cmmn 기반 서비스 레이어 표준) -->
        <dependency>
            <groupId>org.egovframe.rte</groupId>
            <artifactId>%s</artifactId>
%s            <exclusions>
                <exclusion>
                    <groupId>org.springframework</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
                <!-- log4j-slf4j2-impl(SLF4J→Log4j2)이 Spring Boot의 log4j-to-slf4j(Log4j→SLF4J)와
                     순환 충돌을 일으키므로 제외 — Spring Boot 기본 Logback 사용 -->
                <exclusion>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j-slf4j2-impl</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <!-- DB -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter-test</artifactId>
            <version>%s</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
""".formatted(parentGroupId, parentArtifactId, parentVersion,
              s.groupId(), s.artifactId(), projectName,
              javaVer, egovVersionProp,
              mbsbVer,
              thymeleafDeps,
              fdlCmmnId, egovRteVersion,
              mbsbVer);
    }
}
