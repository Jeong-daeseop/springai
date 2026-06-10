package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;
import org.springframework.stereotype.Component;

import static com.krdevops.springai.service.initializr.FilePlanFactory.*;

@Component
public class WarBuildGradleBuilder {

    public String build(ProjectSpec s) {
        String egovVer          = s.cap().egovParent() ? EGOV_50 : EGOV_43;
        String javaVer          = s.cap().java17()         ? JAVA_17          : JAVA_11;
        String springVer        = s.cap().spring6()        ? SPRING_6         : SPRING_5;
        String mybatisSpringVer = s.cap().myBatisSpring3() ? MYBATIS_SPRING_3 : MYBATIS_SPRING_2;
        String egovGradleDeps = s.cap().hyphenArtifactId()
            ? """
    // eGovFrame 5.0 (새 artifactId 명명 규칙)
    implementation "org.egovframe.rte:egovframe-rte-ptl-mvc:${egovVersion}"
    implementation "org.egovframe.rte:egovframe-rte-psl-dataaccess:${egovVersion}"
    implementation "org.egovframe.rte:egovframe-rte-fdl-cmmn:${egovVersion}" """
            : """
    // eGovFrame 4.3 (기존 artifactId 명명 규칙)
    implementation "org.egovframe.rte:org.egovframe.rte.ptl.mvc:${egovVersion}"
    implementation "org.egovframe.rte:org.egovframe.rte.psl.dataaccess:${egovVersion}"
    implementation "org.egovframe.rte:org.egovframe.rte.fdl.cmmn:${egovVersion}" """;
        String servletDeps = s.cap().jakarta()
            ? """
    providedCompile 'jakarta.servlet:jakarta.servlet-api:6.0.0'
    providedCompile 'jakarta.servlet.jsp:jakarta.servlet.jsp-api:3.1.1'
    implementation  'jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.0'
    implementation  'org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1'"""
            : """
    providedCompile 'javax.servlet:javax.servlet-api:4.0.1'
    providedCompile 'javax.servlet.jsp:javax.servlet.jsp-api:2.3.3'
    implementation  'javax.servlet:jstl:1.2'""";
        String validationDeps = s.cap().jakarta()
            ? """
    implementation 'jakarta.validation:jakarta.validation-api:3.0.2'
    implementation 'org.hibernate.validator:hibernate-validator:8.0.1.Final'
    implementation 'org.glassfish:jakarta.el:4.0.2'"""
            : """
    implementation 'javax.validation:validation-api:2.0.1.Final'
    implementation 'org.hibernate.validator:hibernate-validator:6.2.5.Final'
    implementation 'org.glassfish:jakarta.el:3.0.4'""";
        return """
plugins {
    id 'java'
    id 'war'
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

ext {
    egovVersion   = '%s'
    springVersion = '%s'
}

dependencies {
%s

    // MyBatis
    implementation 'org.mybatis:mybatis:%s'
    implementation 'org.mybatis:mybatis-spring:%s'

    // DB
    implementation 'com.mysql:mysql-connector-j:8.4.0'
    implementation 'com.zaxxer:HikariCP:5.1.0'

    // Servlet / JSP
%s

    // Validation
%s

    // Lombok
    compileOnly         'org.projectlombok:lombok:1.18.32'
    annotationProcessor 'org.projectlombok:lombok:1.18.32'

    // Test
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testImplementation "org.springframework:spring-test:${springVersion}"
}

tasks.named('test') { useJUnitPlatform() }
""".formatted(s.groupId(), javaVer, egovVer, springVer, egovGradleDeps, MYBATIS_35, mybatisSpringVer, servletDeps, validationDeps);
    }
}
