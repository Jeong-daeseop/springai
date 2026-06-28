<?xml version="1.0" encoding="UTF-8"?>
<!--
================================================================
eGovFrame 5.0 Spring Security 설정
================================================================
구조: EgovSecurityConfig POJO Bean (설정값) +
      EgovSecurityConfiguration (RTE Java Config — SecurityFilterChain 자동 구성)
XSD:  spring-beans.xsd (Spring 6 기반) — egov-security 네임스페이스 5.0에서 제거됨
사용: 반드시 javaConfig(5.0) 과 함께 사용
      getSecurityTemplate("javaconfig", pkg, "5.0")
      → EgovProjectSecurityConfig.java (@Import(EgovSecurityConfiguration.class))
================================================================
-->
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!--
    EgovSecurityConfig: 순수 POJO — Spring Security API 의존성 없음
    EgovSecurityConfiguration(@Import)이 이 Bean을 읽어 SecurityFilterChain 자동 구성:
      - SecurityFilterChain (URL 접근제어 / 세션 / 보안헤더 / CSRF)
      - AuthenticationManager (DaoAuthenticationProvider + RoleHierarchyAuthoritiesMapper)
      - EgovJdbcUserDetailsManager (사용자/권한 SQL 조회)
      - EgovMultipleRoleAuthorizationManager (DB URL 권한 동적 로드)
    -->
    <bean id="securityConfig"
        class="org.egovframe.rte.fdl.security.config.EgovSecurityConfig">

        <!-- ① 로그인 / 로그아웃 URL -->
        <property name="loginUrl"
            value="/uat/uia/egovLoginUsr.do"/>
        <property name="loginProcessUrl"
            value="/uat/uia/actionLogin.do"/>
        <property name="logoutUrl"
            value="/uat/uia/actionLogout.do"/>
        <property name="logoutSuccessUrl"
            value="/main/Main.do"/>
        <!-- ⚠️ 프로젝트별 URL 변경 필요 -->
        <property name="loginFailureUrl"
            value="/uat/uia/egovLoginUsr.do?login_error=1"/>
        <property name="accessDeniedUrl"
            value="/main/accessDenied.do"/>

        <!-- ② 로그인 성공 처리 -->
        <!-- ⚠️ 실제 메인 URL로 변경 필요 -->
        <property name="defaultTargetUrl"
            value="/main/Main.do"/>
        <!-- true: 항상 defaultTargetUrl 이동 / false: 이전 요청 URL 우선 -->
        <property name="alwaysUseDefaultTargetUrl"
            value="true"/>

        <!-- ③ DataSource (egov.dataSource alias — 순환참조 방지) -->
        <property name="dataSource"
            value="egov.dataSource"/>

        <!-- ④ 사용자 조회 SQL (반환 컬럼: username, password, enabled + 추가 컬럼) -->
        <!-- ⚠️ 프로젝트 테이블명/컬럼명으로 변경 필요 -->
        <property name="jdbcUsersByUsernameQuery"
            value="SELECT EMPLYR_ID, USER_NM, PASSWORD, 1 ENABLED, ORGNZT_ID
                   FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?"/>

        <!-- ⑤ 권한 조회 SQL (반환 컬럼: username, authority) -->
        <!-- ⚠️ 프로젝트 테이블명/컬럼명으로 변경 필요 -->
        <property name="jdbcAuthoritiesByUsernameQuery"
            value="SELECT A.SCRTY_DTRMN_TRGET_ID USER_ID, A.AUTHOR_CODE AUTHORITY
                   FROM COMTNEMPLYRSCRTYESTBS A, COMTNEMPLYRINFO B
                   WHERE A.SCRTY_DTRMN_TRGET_ID = B.EMPLYR_ID AND B.EMPLYR_ID = ?"/>

        <!-- ⑥ ResultSet → LoginVO → EgovUserDetails 변환 클래스 -->
        <!-- ⚠️ jdbcMapClass: sessionMapping 템플릿 생성 클래스와 일치 필요
                 {packageName}.uat.uia.service.impl.EgovSessionMapping -->
        <property name="jdbcMapClass"
            value="${packageName}.uat.uia.service.impl.EgovSessionMapping"/>

        <!-- ⑦ 비밀번호 해시 알고리즘 -->
        <!-- ⚠️ eGovFrame 표준: sha-256 (EgovFileScrty.encryptPassword와 일치 필요)
             BCrypt 사용 시: value="bcrypt" + hashBase64 제거 -->
        <property name="hash"
            value="sha-256"/>
        <!-- sha-256 + Base64 인코딩 (eGovFrame 표준) -->
        <property name="hashBase64"
            value="true"/>

        <!-- ⑧ 동시 세션 제어 -->
        <property name="concurrentMaxSessons"
            value="1"/>
        <property name="concurrentExpiredUrl"
            value="/EgovContent.do"/>
        <!-- false: 기존 세션 만료 (eGovFrame 표준) / true: 신규 로그인 차단 -->
        <property name="errorIfMaximumExceeded"
            value="false"/>

        <!-- ⑨ 보안 헤더 -->
        <!-- X-Content-Type-Options: nosniff -->
        <property name="sniff"
            value="true"/>
        <!-- X-Frame-Options: SAMEORIGIN (클릭재킹 방지) -->
        <property name="xframeOptions"
            value="SAMEORIGIN"/>
        <!-- X-XSS-Protection: 1; mode=block -->
        <property name="xssProtection"
            value="true"/>
        <!-- Cache-Control 헤더 비활성화 (eGovFrame 표준) -->
        <property name="cacheControl"
            value="false"/>

        <!-- ⑩ CSRF -->
        <!-- ⚠️ EgovSpringSecurityLoginFilter 사용 시 false 권장
             JSP form 기반 표준 CSRF 보호 필요 시 true -->
        <property name="csrf"
            value="false"/>
        <property name="csrfAccessDeniedUrl"
            value="/egovCSRFAccessDenied.do"/>

        <!-- ⑪ 요청 매처 타입 (regex: 정규식 / ant: Ant 패턴) -->
        <property name="requestMatcherType"
            value="regex"/>

        <!-- ⑫ 인증 없이 접근 허용 경로 (쉼표 구분) -->
        <property name="permitAllList"
            value="/css/**,/images/**,/js/**,\A/WEB-INF/jsp/.*\Z"/>

        <!-- ⑬ URL 권한 매핑 SQL (EgovMultipleRoleAuthorizationManager가 동적 로드) -->
        <!-- ⚠️ 프로젝트 테이블명/컬럼명으로 변경 필요 -->
        <property name="sqlRolesAndUrl"
            value="SELECT a.ROLE_PTTRN url, b.AUTHOR_CODE authority
                   FROM COMTNROLEINFO a, COMTNAUTHORROLERELATE b
                   WHERE a.ROLE_CODE = b.ROLE_CODE AND a.ROLE_TY = 'url'
                   ORDER BY a.ROLE_SORT"/>

        <!-- ⑭ 메서드 권한 매핑 SQL (supportMethod=true인 경우) -->
        <property name="sqlRolesAndMethod"
            value="SELECT a.ROLE_PTTRN method, b.AUTHOR_CODE authority
                   FROM COMTNROLEINFO a, COMTNAUTHORROLERELATE b
                   WHERE a.ROLE_CODE = b.ROLE_CODE AND a.ROLE_TY = 'method'
                   ORDER BY a.ROLE_SORT"/>

        <!-- ⑮ 포인트컷 권한 매핑 SQL (supportPointcut=true인 경우) -->
        <property name="sqlRolesAndPointcut"
            value="SELECT a.ROLE_PTTRN pointcut, b.AUTHOR_CODE authority
                   FROM COMTNROLEINFO a, COMTNAUTHORROLERELATE b
                   WHERE a.ROLE_CODE = b.ROLE_CODE AND a.ROLE_TY = 'pointcut'
                   ORDER BY a.ROLE_SORT"/>

        <!-- ⑯ ROLE 계층 SQL -->
        <property name="sqlHierarchicalRoles"
            value="SELECT a.CHLDRN_ROLE child, a.PARNTS_ROLE parent
                   FROM COMTNROLES_HIERARCHY a
                   LEFT JOIN COMTNROLES_HIERARCHY b ON (a.CHLDRN_ROLE = b.PARNTS_ROLE)"/>

        <!-- ⑰ 메서드/포인트컷 보안 활성화 -->
        <property name="supportMethod"
            value="true"/>
        <property name="supportPointcut"
            value="false"/>

    </bean>

</beans>
