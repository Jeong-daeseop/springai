package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;
import org.springframework.stereotype.Component;

import static com.krdevops.springai.service.initializr.FilePlanFactory.supportsJakarta;
import static com.krdevops.springai.service.initializr.FilePlanFactory.supportsSpring6;

@Component
public class WebXmlBuilder {

    public String build(ProjectSpec s) {
        String artifactId  = s.artifactId();
        String egovVersion = s.egovVersion();

        String ns      = supportsJakarta(egovVersion) ? "https://jakarta.ee/xml/ns/jakartaee" : "http://xmlns.jcp.org/xml/ns/javaee";
        String xsdLoc  = supportsJakarta(egovVersion)
            ? "https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
            : "http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd";
        String ver     = supportsJakarta(egovVersion) ? "6.0" : "4.0";
        String welcomeFile = s.thymeleaf() ? "index.html" : "index.jsp";
        String multipartConfig = supportsSpring6(egovVersion)
            ? """

        <multipart-config>
            <!-- 파일 1개 최대 50MB, 요청 전체 최대 100MB, 임계값 초과 시 디스크 저장 -->
            <max-file-size>52428800</max-file-size>
            <max-request-size>104857600</max-request-size>
            <file-size-threshold>1048576</file-size-threshold>
        </multipart-config>"""
            : "";
        return """
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="%s"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="%s"
         version="%s">

    <display-name>%s</display-name>

    <context-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>/WEB-INF/spring/root-context.xml</param-value>
    </context-param>
    <listener>
        <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
    </listener>

    <filter>
        <filter-name>encodingFilter</filter-name>
        <filter-class>org.springframework.web.filter.CharacterEncodingFilter</filter-class>
        <init-param><param-name>encoding</param-name><param-value>UTF-8</param-value></init-param>
        <init-param><param-name>forceEncoding</param-name><param-value>true</param-value></init-param>
    </filter>
    <filter-mapping>
        <filter-name>encodingFilter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>

    <servlet>
        <servlet-name>dispatcher</servlet-name>
        <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value>/WEB-INF/spring/appServlet/servlet-context.xml</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>%s
    </servlet>
    <servlet-mapping>
        <servlet-name>dispatcher</servlet-name>
        <url-pattern>*.do</url-pattern>
    </servlet-mapping>

    <welcome-file-list>
        <welcome-file>%s</welcome-file>
    </welcome-file-list>

    <error-page><error-code>404</error-code><location>/WEB-INF/jsp/egovframework/error/error404.jsp</location></error-page>
    <error-page><error-code>500</error-code><location>/WEB-INF/jsp/egovframework/error/error500.jsp</location></error-page>

</web-app>
""".formatted(ns, xsdLoc, ver, artifactId, multipartConfig, welcomeFile);
    }
}
