⏺ contextSecurity50() 전면 재작성 상세 설명
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
왜 구조가 완전히 달라지나

4.3 (egov-security 네임스페이스 방식):                                                                                                                                                                     
context-security.xml이 Spring Security 설정 전체를 직접 담당
→ <http>, <authentication-manager>, <beans:bean> 11개 직접 선언                                                                                                                                          
→ egov-security namespace handler가 이걸 파싱해서 FilterChain 구성

5.0 (EgovSecurityConfig POJO 방식):                                                                                                                                                                      
context-security.xml은 설정값만 담는 데이터 파일                                                                                                                                                           
→ <bean class="EgovSecurityConfig"> 1개만 선언                                                                                                                                                           
→ EgovSecurityConfiguration(RTE Java Config)이 이 Bean을 읽어서 FilterChain 구성
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
변경 전 (현재 contextSecurity50 생성 내용)

  <?xml version="1.0" encoding="UTF-8"?>                                                                                                                                                                     
<beans:beans xmlns="...security"                                                                                                                                                                         
xmlns:beans="...beans"                                                                                                                                                                                 
xmlns:egov-security="..."          ← 5.0에서 제거됨                                                                                                                                                  
xsi:schemaLocation="...                                                                                                                                                                                
egov-security-5.0.0.xsd">     ← 존재하지 않는 XSD

      <egov-security:config .../>        ← 5.0에서 제거된 요소                                                                                                                                               
                                                                                                                                                                                                             
      <http pattern="/css/**" security="none"/>                                                                                                                                                              
      <http auto-config="false" ...>                                                                                                                                                                         
          <form-login .../>                                                                                                                                                                                  
          <logout .../>                                                                                                                                                                                      
          <session-management .../>                                                                                                                                                                        
          <access-denied-handler .../>                                                                                                                                                                       
          <csrf/>                                                                                                                                                                                          
          <custom-filter .../>
      </http>                                                                                                                                                                                                
                                              
      <authentication-manager .../>                                                                                                                                                                          
      <beans:bean id="egovAuthenticationProvider" .../>                                                                                                                                                    
      <beans:bean id="passwordEncoder" .../>                                                                                                                                                                 
      <beans:bean id="egovSecurityFilter" .../>
      <beans:bean id="egovSecurityMetadataSource" .../>                                                                                                                                                      
      <beans:bean id="accessDecisionManager" .../>                                                                                                                                                         
      <beans:bean id="roleHierarchy" .../>                                                                                                                                                                   
      <beans:bean id="loginSuccessHandler" .../>
      <beans:bean id="loginFailureHandler" .../>                                                                                                                                                             
      <beans:bean id="accessDeniedHandler" .../>                                                                                                                                                           
</beans:beans>                                                                                                                                                                                             
→ Bean 11개 + 각종 설정 = 약 170줄
                                              
---                                                                                                                                                                                                        
변경 후 (올바른 contextSecurity50 구조)

  <?xml version="1.0" encoding="UTF-8"?>                                                                                                                                                                   
  <!-- 5.0: egov-security 네임스페이스 없음. spring-beans.xsd만 사용 -->                                                                                                                                     
<beans xmlns="http://www.springframework.org/schema/beans"                                                                                                                                               
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="                                                                                                                                                                                   
http://www.springframework.org/schema/beans                                                                                                                                                        
http://www.springframework.org/schema/beans/spring-beans.xsd">

      <bean id="securityConfig"                                                                                                                                                                            
          class="org.egovframe.rte.fdl.security.config.EgovSecurityConfig">                                                                                                                                  
                                                                                                                                                                                                             
          ... 32개 property                   
                                                                                                                                                                                                             
      </bean>                                                                                                                                                                                                

  </beans>                                                                                                                                                                                                   
  → Bean 1개 = 약 60줄                                                                                                                                                                                       
                                                                                                                                                                                                           
---
32개 Property 전체 상세

그룹 1 — 로그인/로그아웃 URL (6개)

  <!-- 로그인 화면 URL -->                                                                                                                                                                                   
<property name="loginUrl"                   
value="/uat/uia/egovLoginUsr.do"/>

  <!-- 로그인 처리 URL (POST 제출 대상) -->                                                                                                                                                                  
<property name="loginProcessUrl"                                                                                                                                                                         
value="/uat/uia/actionLogin.do"/>

  <!-- 로그아웃 처리 URL -->                                                                                                                                                                                 
<property name="logoutUrl"                                                                                                                                                                                 
value="/uat/uia/actionLogout.do"/>

  <!-- 로그아웃 성공 후 이동 URL -->                                                                                                                                                                       
<property name="logoutSuccessUrl"
value="/main/Main.do"/>

  <!-- 로그인 실패 URL -->                                                                                                                                                                                   
<property name="loginFailureUrl"                                                                                                                                                                         
value="/uat/uia/egovLoginUsr.do?login_error=1"/>

  <!-- 접근 거부(403) URL -->                                                                                                                                                                                
<property name="accessDeniedUrl"                                                                                                                                                                         
value="/main/accessDenied.do"/>
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
그룹 2 — 로그인 성공 처리 (2개)

  <!-- 로그인 성공 후 이동 URL -->        
<property name="defaultTargetUrl"
value="/main/Main.do"/>

  <!-- true: 항상 defaultTargetUrl로 이동 (이전 요청 URL 무시)                                                                                                                                               
       false: SavedRequest 있으면 이전 요청 URL로 이동 (기본값) -->                                                                                                                                          
<property name="alwaysUseDefaultTargetUrl"  
value="true"/>
                                                                                                                                                                                                           
---                                                                                                                                                                                                        
그룹 3 — DataSource / 사용자·권한 SQL (4개)

  <!-- DataSource Bean alias (순환참조 방지용 egov.dataSource alias) -->                                                                                                                                     
<property name="dataSource"                                                                                                                                                                              
value="egov.dataSource"/>

  <!-- 사용자 조회 SQL                                                                                                                                                                                       
       반환 컬럼 순서: username, password, enabled                                                                                                                                                         
       추가 컬럼: 세션 매핑에 활용 -->                                                                                                                                                                       
<property name="jdbcUsersByUsernameQuery"                                                                                                                                                                  
value="SELECT USER_ID, USER_NM, PASSWORD, 1 ENABLED, DEPT_ID
FROM TN_USERS                                                                                                                                                                                   
WHERE USER_ID = ?"/>

  <!-- 권한 조회 SQL                                                                                                                                                                                         
       반환 컬럼 순서: username, authority -->                                                                                                                                                               
<property name="jdbcAuthoritiesByUsernameQuery"                                                                                                                                                            
value="SELECT A.SCRTY_DTRMN_TRGET_ID USER_ID, A.AUTHOR_CODE AUTHORITY                                                                                                                                  
FROM TN_EMPLYRSCRTYESTBS A, TN_USERS B                                                                                                                                                        
WHERE A.SCRTY_DTRMN_TRGET_ID = B.USER_ID                                                                                                                                                        
AND B.USER_ID = ?"/>

  <!-- ResultSet → LoginVO → EgovUserDetails 변환 클래스                                                                                                                                                     
       LET 계열: egovframework.let.uat.uia.service.impl.EgovSessionMapping                                                                                                                                   
       COM 계열: egovframework.com.uat.uia.service.impl.EgovSessionMapping -->                                                                                                                               
<property name="jdbcMapClass"                                                                                                                                                                              
value="egovframework.com.uat.uia.service.impl.EgovSessionMapping"/>
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
그룹 4 — 비밀번호 해시 (2개)

  <!-- 해시 알고리즘: sha-256 또는 bcrypt                                                                                                                                                                  
       eGovFrame 표준: sha-256 (EgovFileScrty.encryptPassword와 일치 필요)                                                                                                                                   
       BCrypt 사용 시: value="bcrypt" -->                                                                                                                                                                  
<property name="hash"                       
value="sha-256"/>

  <!-- Base64 인코딩 여부                                                                                                                                                                                    
       sha-256 + true: Base64 인코딩 (eGovFrame 표준)                                                                                                                                                        
       bcrypt: 이 설정 무관 -->                                                                                                                                                                              
<property name="hashBase64"                                                                                                                                                                              
value="true"/>
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
그룹 5 — 세션 / 동시접속 (3개)

  <!-- 최대 동시 세션 수 -->                                                                                                                                                                               
<property name="concurrentMaxSessons"       
value="1"/>

  <!-- 동시 세션 초과 시 만료 URL -->                                                                                                                                                                        
<property name="concurrentExpiredUrl"                                                                                                                                                                    
value="/EgovContent.do"/>

  <!-- true: 최대 세션 초과 시 신규 로그인 차단
       false: 기존 세션 만료 (eGovFrame 표준) -->                                                                                                                                                            
<property name="errorIfMaximumExceeded"                                                                                                                                                                    
value="false"/>
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
그룹 6 — 보안 헤더 (4개)

  <!-- X-Content-Type-Options: nosniff                                                                                                                                                                     
       true: 브라우저 MIME 타입 스니핑 방지 -->                                                                                                                                                              
<property name="sniff"                                                                                                                                                                                   
value="true"/>

  <!-- X-Frame-Options                                                                                                                                                                                       
       SAMEORIGIN: 동일 도메인 iframe만 허용 (클릭재킹 방지)                                                                                                                                                 
       DENY: 모든 iframe 차단 -->                                                                                                                                                                            
<property name="xframeOptions"                                                                                                                                                                           
value="SAMEORIGIN"/>

  <!-- X-XSS-Protection: 1; mode=block                                                                                                                                                                       
       true: 브라우저 XSS 필터 활성화 -->                                                                                                                                                                    
<property name="xssProtection"                                                                                                                                                                             
value="true"/>

  <!-- Cache-Control: no-cache, no-store, must-revalidate                                                                                                                                                    
       false: 캐시 제어 헤더 비활성화 (eGovFrame 표준)                                                                                                                                                     
       true: 캐시 금지 헤더 전송 -->                                                                                                                                                                         
<property name="cacheControl"           
value="false"/>
                                                                                                                                                                                                             
---
그룹 7 — CSRF (2개)

  <!-- CSRF 토큰 검증 활성화 여부                                                                                                                                                                            
       false: 비활성화 (bopr 표준 — EgovSpringSecurityLoginFilter가 직접 인증 처리)                                                                                                                        
       true: 활성화 (JSP form 기반 표준 CSRF 보호) -->
<property name="csrf"                       
value="false"/>

  <!-- CSRF 검증 실패 시 이동 URL (csrf=true인 경우만 의미 있음) -->                                                                                                                                         
<property name="csrfAccessDeniedUrl"                                                                                                                                                                       
value="/egovCSRFAccessDenied.do"/>
                                                                                                                                                                                                             
---                                     
그룹 8 — 요청 매처 타입 (1개)

  <!-- URL 패턴 매처 타입                 
       regex: 정규식 패턴 (eGovFrame 표준 — ROLE_PTTRN이 정규식)                                                                                                                                             
       ant: Ant 패턴 (/path/**) -->                                                                                                                                                                          
<property name="requestMatcherType"         
value="regex"/>
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
그룹 9 — 인증 없이 허용할 경로 (1개)

  <!-- 인증 없이 접근 허용 경로 (쉼표 구분)                                                                                                                                                                
       정적 자원 + WEB-INF jsp는 반드시 포함 -->                                                                                                                                                             
<property name="permitAllList"                                                                                                                                                                             
value="/css/**,/images/**,/js/**,\A/WEB-INF/jsp/.*\Z"/>
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
그룹 10 — DB 기반 URL/메서드/포인트컷 권한 매핑 SQL (3개)

  <!-- URL 패턴 → 권한 매핑 SQL                                                                                                                                                                            
       EgovMultipleRoleAuthorizationManager가 이 SQL로 DB 동적 로드                                                                                                                                          
       ROLE_TY = 'url' 조건 필수 -->                                                                                                                                                                         
<property name="sqlRolesAndUrl"             
value="SELECT a.ROLE_PTTRN url, b.AUTHOR_CODE authority                                                                                                                                                
FROM TN_ROLEINFO a, TN_AUTHORROLERELATE b                                                                                                                                                       
WHERE a.ROLE_CODE = b.ROLE_CODE                                                                                                                                                                 
AND a.ROLE_TY = 'url'                                                                                                                                                                           
ORDER BY a.ROLE_SORT"/>

  <!-- 메서드 패턴 → 권한 매핑 SQL (supportMethod=true인 경우)                                                                                                                                               
       ROLE_TY = 'method' 조건 -->                                                                                                                                                                           
<property name="sqlRolesAndMethod"                                                                                                                                                                         
value="SELECT a.ROLE_PTTRN method, b.AUTHOR_CODE authority                                                                                                                                             
FROM TN_ROLEINFO a, TN_AUTHORROLERELATE b
WHERE a.ROLE_CODE = b.ROLE_CODE                                                                                                                                                                 
AND a.ROLE_TY = 'method'                                                                                                                                                                      
ORDER BY a.ROLE_SORT"/>

  <!-- 포인트컷 패턴 → 권한 매핑 SQL (supportPointcut=true인 경우) -->                                                                                                                                       
<property name="sqlRolesAndPointcut"                                                                                                                                                                     
value="SELECT a.ROLE_PTTRN pointcut, b.AUTHOR_CODE authority                                                                                                                                           
FROM TN_ROLEINFO a, TN_AUTHORROLERELATE b
WHERE a.ROLE_CODE = b.ROLE_CODE                                                                                                                                                                 
AND a.ROLE_TY = 'pointcut'                                                                                                                                                                      
ORDER BY a.ROLE_SORT"/>
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
그룹 11 — 역할 계층 SQL (1개)

  <!-- ROLE 계층 구조 조회 SQL                                                                                                                                                                             
       CHILD_ROLE → PARNTS_ROLE 순서 주의 (COMTN계열: CHLDRN_ROLE)
       LEFT JOIN: 계층 트리 전체 조회 -->                                                                                                                                                                    
<property name="sqlHierarchicalRoles"       
value="SELECT a.CHILD_ROLE child, a.PARNTS_ROLE parent                                                                                                                                                 
FROM TN_ROLES_HIERARCHY a                                                                                                                                                                       
LEFT JOIN TN_ROLES_HIERARCHY b ON (a.CHILD_ROLE = b.PARNTS_ROLE)"/>
                                                                                                                                                                                                             
---                                                                                                                                                                                                        
그룹 12 — 메서드/포인트컷 보안 활성화 (2개)

  <!-- 메서드 수준 보안 활성화                                                                                                                                                                               
       true: @Secured, @PreAuthorize 등 메서드 보안 활성화 -->                                                                                                                                             
<property name="supportMethod"                                                                                                                                                                             
value="true"/>

  <!-- 포인트컷 수준 보안 활성화                                                                                                                                                                             
       false: 불필요한 경우 비활성화 -->                                                                                                                                                                     
<property name="supportPointcut"                                                                                                                                                                           
value="false"/>

  ---                                                                                                                                                                                                        
Property 그룹 요약표

┌─────────────────────┬──────┬──────────────────────────────────────────────────────────────────────────────────────────┐                                                                                
│        그룹         │ 개수 │                                           항목                                           │
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤                                                                                  
│ 로그인/로그아웃 URL │ 6    │ loginUrl, loginProcessUrl, logoutUrl, logoutSuccessUrl, loginFailureUrl, accessDeniedUrl │
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤                                                                                  
│ 로그인 성공 처리    │ 2    │ defaultTargetUrl, alwaysUseDefaultTargetUrl                                              │                                                                                
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
│ DataSource / SQL    │ 4    │ dataSource, jdbcUsersByUsernameQuery, jdbcAuthoritiesByUsernameQuery, jdbcMapClass       │
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤                                                                                  
│ 비밀번호 해시       │ 2    │ hash, hashBase64                                                                         │
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤                                                                                  
│ 세션 / 동시접속     │ 3    │ concurrentMaxSessons, concurrentExpiredUrl, errorIfMaximumExceeded                       │                                                                                  
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
│ 보안 헤더           │ 4    │ sniff, xframeOptions, xssProtection, cacheControl                                        │                                                                                  
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤                                                                                
│ CSRF                │ 2    │ csrf, csrfAccessDeniedUrl                                                                │                                                                                  
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
│ 요청 매처           │ 1    │ requestMatcherType                                                                       │                                                                                  
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤                                                                                
│ 허용 경로           │ 1    │ permitAllList                                                                            │                                                                                  
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
│ DB 권한 SQL         │ 3    │ sqlRolesAndUrl, sqlRolesAndMethod, sqlRolesAndPointcut                                   │                                                                                  
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤                                                                                
│ 역할 계층 SQL       │ 1    │ sqlHierarchicalRoles                                                                     │                                                                                  
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
│ 메서드/포인트컷     │ 2    │ supportMethod, supportPointcut                                                           │                                                                                  
├─────────────────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤                                                                                
│ 합계                │ 31   │                                                                                          │                                                                                  
└─────────────────────┴──────┴──────────────────────────────────────────────────────────────────────────────────────────┘
                                                                                                                                                                                                             
---                                                                                                                                                                                                      
4.3 vs 5.0 context-security.xml 핵심 차이

┌────────────────────────────┬───────────────────────────────────────────────┬────────────────────────────────────────────────┐                                                                            
│            항목            │                      4.3                      │                      5.0                       │                                                                          
├────────────────────────────┼───────────────────────────────────────────────┼────────────────────────────────────────────────┤                                                                            
│ XML 루트                   │ <beans:beans> (security + beans 네임스페이스) │ <beans> (beans만)                              │
├────────────────────────────┼───────────────────────────────────────────────┼────────────────────────────────────────────────┤                                                                            
│ egov-security 네임스페이스 │ 있음                                          │ 없음                                           │                                                                          
├────────────────────────────┼───────────────────────────────────────────────┼────────────────────────────────────────────────┤
│ 보안 설정 선언 방식        │ <egov-security:config> + <http> + Bean 11개   │ <bean class="EgovSecurityConfig"> 1개          │
├────────────────────────────┼───────────────────────────────────────────────┼────────────────────────────────────────────────┤                                                                            
│ FilterChain 구성 주체      │ XML namespace handler                         │ EgovSecurityConfiguration (RTE Java Config)    │                                                                            
├────────────────────────────┼───────────────────────────────────────────────┼────────────────────────────────────────────────┤                                                                            
│ URL 권한 SQL               │ 없음 (EgovReloadableFilter가 별도 처리)       │ sqlRolesAndUrl property로 직접 지정            │                                                                            
├────────────────────────────┼───────────────────────────────────────────────┼────────────────────────────────────────────────┤                                                                          
│ 비밀번호 해시 설정         │ 없음 (별도 Bean)                              │ hash / hashBase64 property                     │                                                                            
├────────────────────────────┼───────────────────────────────────────────────┼────────────────────────────────────────────────┤                                                                          
│ 보안 헤더 설정             │ 없음                                          │ sniff / xframeOptions / xssProtection property │                                                                            
└────────────────────────────┴───────────────────────────────────────────────┴────────────────────────────────────────────────┘          