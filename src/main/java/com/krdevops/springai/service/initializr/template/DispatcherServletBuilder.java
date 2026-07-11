package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;
import org.springframework.stereotype.Component;

import static com.krdevops.springai.service.initializr.FilePlanFactory.supportsSpring6;

@Component
public class DispatcherServletBuilder {

    public String build(ProjectSpec s) {
        String scanBasePackage = PackageScanBase.from(s.packageName());
        String egovVersion  = s.egovVersion();

        String multipartBean = supportsSpring6(egovVersion)
            ? """
    <!-- Spring 6+ : StandardServletMultipartResolver (CommonsMultipartResolver 제거됨)
         파일 크기 제한은 web.xml <multipart-config> 에서 설정 -->
    <bean id="multipartResolver"
          class="org.springframework.web.multipart.support.StandardServletMultipartResolver"/>"""
            : """
    <!-- Spring 5 : CommonsMultipartResolver -->
    <bean id="multipartResolver"
          class="org.springframework.web.multipart.commons.CommonsMultipartResolver">
        <property name="defaultEncoding" value="UTF-8"/>
        <property name="maxUploadSize"   value="52428800"/>
    </bean>""";

        String validatorBean = """
    <!-- Bean Validation (JSR-303/380) — Hibernate Validator 구현체 자동 감지 -->
    <bean id="validator"
          class="org.springframework.validation.beanvalidation.LocalValidatorFactoryBean"/>""";

        String methodValidationBean = supportsSpring6(egovVersion) ? "" : """

    <!-- 메서드 레벨 파라미터 검증 활성화 (Spring 5 / eGovFrame 4.3 — AOP 기반) -->
    <bean class="org.springframework.validation.beanvalidation.MethodValidationPostProcessor">
        <property name="validator" ref="validator"/>
    </bean>""";

        String thymeleafViewResolver = s.thymeleaf()
            ? """

    <bean id="templateResolver"
          class="%s.templateresolver.SpringResourceTemplateResolver">
        <property name="prefix" value="classpath:/templates/"/>
        <property name="suffix" value=".html"/>
        <property name="templateMode" value="HTML"/>
        <property name="characterEncoding" value="UTF-8"/>
        <property name="cacheable" value="false"/>
    </bean>

    <bean id="templateEngine"
          class="%s.SpringTemplateEngine">
        <property name="templateResolver" ref="templateResolver"/>
        <property name="additionalDialects">
            <set>
                <bean class="nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect"/>
            </set>
        </property>
    </bean>

    <bean class="%s.view.ThymeleafViewResolver">
        <property name="templateEngine" ref="templateEngine"/>
        <property name="characterEncoding" value="UTF-8"/>
        <property name="order" value="1"/>
    </bean>
""".formatted(thymeleafPackage(egovVersion), thymeleafPackage(egovVersion), thymeleafPackage(egovVersion))
            : "";

        String jspViewResolverOrder = s.thymeleaf() ? "2" : "1";

        return """
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:mvc="http://www.springframework.org/schema/mvc"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           http://www.springframework.org/schema/context/spring-context.xsd
           http://www.springframework.org/schema/mvc
           http://www.springframework.org/schema/mvc/spring-mvc.xsd">

    <context:component-scan base-package="%s" use-default-filters="false">
        <context:include-filter type="annotation"
            expression="org.springframework.stereotype.Controller"/>
    </context:component-scan>

%s%s

    <mvc:annotation-driven validator="validator"/>
    <mvc:resources mapping="/resources/**" location="/resources/"/>

%s
    <bean class="org.springframework.web.servlet.view.InternalResourceViewResolver">
        <property name="prefix" value="/WEB-INF/jsp/"/>
        <property name="suffix" value=".jsp"/>
        <property name="order"  value="%s"/>
    </bean>

%s

</beans>
""".formatted(scanBasePackage, validatorBean, methodValidationBean, multipartBean,
                jspViewResolverOrder, thymeleafViewResolver);
    }

    private static String thymeleafPackage(String egovVersion) {
        return supportsSpring6(egovVersion) ? "org.thymeleaf.spring6" : "org.thymeleaf.spring5";
    }
}
