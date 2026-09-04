package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;
import org.springframework.stereotype.Component;

import static com.krdevops.springai.service.initializr.FilePlanFactory.*;

@Component
public class BootBuildGradleBuilder {

    public String build(ProjectSpec s) {
        String sbVer     = s.cap().boot3()              ? SPRING_BOOT_3              : SPRING_BOOT_2;
        String javaVer   = s.cap().java17()             ? JAVA_17                    : JAVA_11;
        String mbsbVer   = s.cap().boot3()              ? MYBATIS_SB3                : MYBATIS_SB2;
        String egovVer   = s.cap().egovParent()         ? EGOV_50                    : EGOV_43;
        String fdlCmmnId = s.cap().hyphenArtifactId()   ? "egovframe-rte-fdl-cmmn"  : "org.egovframe.rte.fdl.cmmn";
        String thymeleafDeps = !s.thymeleaf() ? "" : """

    // Thymeleaf (viewType=thymeleaf) — ViewResolver/LayoutDialect는 Boot auto-configuration이 구성
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect'""";
        return """
plugins {
    id 'java'
    id 'org.springframework.boot' version '%s'
    id 'io.spring.dependency-management' version '1.1.5'
}

group   = '%s'
version = '1.0.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(%s)
    }
}

repositories {
    mavenCentral()
    maven { url 'https://maven.egovframe.go.kr/maven/' }
}

dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // MyBatis Spring Boot Starter
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:%s'
%s

    // eGovFrame (fdl.cmmn 서비스 레이어 표준)
    implementation("org.egovframe.rte:%s:%s") {
        exclude group: 'org.springframework'
        // log4j-slf4j2-impl(SLF4J→Log4j2)이 Spring Boot의 log4j-to-slf4j(Log4j→SLF4J)와
        // 순환 충돌을 일으키므로 제외 — Spring Boot 기본 Logback 사용
        exclude group: 'org.apache.logging.log4j', module: 'log4j-slf4j2-impl'
    }

    // DB
    runtimeOnly 'com.mysql:mysql-connector-j'

    // Lombok
    compileOnly         'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:%s'
}

tasks.named('test') { useJUnitPlatform() }
""".formatted(sbVer, s.groupId(), javaVer, mbsbVer, thymeleafDeps, fdlCmmnId, egovVer, mbsbVer);
    }
}
