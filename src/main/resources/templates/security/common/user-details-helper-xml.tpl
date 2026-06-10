<?xml version="1.0" encoding="UTF-8"?>
<!--
     context-egovuserdetailshelper.xml
     위치: src/main/resources/egovframework/spring/com/context-egovuserdetailshelper.xml

     인증 방식을 globals.properties Globals.Auth 값에 따라 Spring Profile로 분기:
       dummy    : 인증 없이 테스트용 더미 사용자 사용
       session  : HttpSession 기반 직접 인증 (Spring Security 미사용)
       security : Spring Security 기반 인증 (운영 방식)

     globals.properties:
       Globals.Auth = security

     web.xml 또는 Spring Boot에서 spring.profiles.active=security 로 활성화
-->
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- [dummy] 인증 없이 테스트용 더미 사용자 -->
    <beans profile="dummy">
        <bean id="egovUserDetailsHelper"
            class="egovframework.com.cmm.util.EgovUserDetailsHelper">
            <property name="egovUserDetailsService">
                <bean class="egovframework.com.cmm.service.impl.EgovUserDetailsSessionServiceImpl"/>
            </property>
        </bean>
    </beans>

    <!-- [session] HttpSession 기반 직접 인증 (Spring Security 미사용) -->
    <beans profile="session">
        <bean id="egovUserDetailsHelper"
            class="egovframework.com.cmm.util.EgovUserDetailsHelper">
            <property name="egovUserDetailsService">
                <bean class="egovframework.com.cmm.service.impl.EgovUserDetailsSessionServiceImpl"/>
            </property>
        </bean>
    </beans>

    <!-- [security] Spring Security 기반 인증 (운영 방식) -->
    <beans profile="security">
        <bean id="egovUserDetailsHelper"
            class="egovframework.com.cmm.util.EgovUserDetailsHelper">
            <property name="egovUserDetailsService">
                <!--
                     EgovUserDetailsSecurityServiceImpl:
                     EgovUserDetailsHelper를 통해 Spring Security에서 인증정보 조회
                     SecurityContextHolder.getContext().getAuthentication() 위임
                -->
                <bean class="egovframework.com.sec.ram.service.impl.EgovUserDetailsSecurityServiceImpl"/>
            </property>
        </bean>
    </beans>

</beans>
