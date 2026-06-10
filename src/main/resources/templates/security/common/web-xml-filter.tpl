<!-- ============================================================
     web.xml — eGovFrame Security 필터 체인 (선언 순서 엄수)

     ① CharacterEncodingFilter    : UTF-8 인코딩 (/*.do)
     ② HTMLTagFilter              : XSS 방어 (/*.do)
     ③ LoginPolicyFilter          : 비밀번호 만료/계정 잠금 체크 (로그인 URL)
     ④ EgovSpringSecurityLoginFilter : DB 인증 핵심 필터 (/*.do)
     ⑤ springSecurityFilterChain  : Spring Security 전체 (/*)
     ⑥ EgovSpringSecurityLogoutFilter : 세션 초기화 (로그아웃 URL)

     ⚠️ ④ EgovSpringSecurityLoginFilter는 반드시 ⑤ springSecurityFilterChain 앞에 위치
        순서가 바뀌면 DB 인증 없이 Spring Security formLogin이 먼저 처리됨
============================================================ -->

<!-- ① CharacterEncodingFilter -->
<filter>
    <filter-name>encodingFilter</filter-name>
    <filter-class>
        org.springframework.web.filter.CharacterEncodingFilter
    </filter-class>
    <init-param>
        <param-name>encoding</param-name>
        <param-value>UTF-8</param-value>
    </init-param>
    <init-param>
        <param-name>forceEncoding</param-name>
        <param-value>true</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>encodingFilter</filter-name>
    <url-pattern>*.do</url-pattern>
</filter-mapping>

<!-- ② HTMLTagFilter: XSS 방어 — 모든 파라미터의 HTML 태그 제거 -->
<filter>
    <filter-name>HTMLTagFilter</filter-name>
    <filter-class>
        egovframework.com.cmm.filter.HTMLTagFilter
    </filter-class>
</filter>
<filter-mapping>
    <filter-name>HTMLTagFilter</filter-name>
    <url-pattern>*.do</url-pattern>
</filter-mapping>

<!-- ③ LoginPolicyFilter: 비밀번호 만료/계정 잠금 정책 체크 (로그인 처리 URL만) -->
<filter>
    <filter-name>loginPolicyFilter</filter-name>
    <filter-class>
        ${packageName}.uat.uap.filter.EgovLoginPolicyFilter
    </filter-class>
</filter>
<filter-mapping>
    <filter-name>loginPolicyFilter</filter-name>
    <url-pattern>/uat/uia/actionLogin.do</url-pattern>
</filter-mapping>

<!-- ④ EgovSpringSecurityLoginFilter: DB 인증 핵심 필터
     ⚠️ 반드시 springSecurityFilterChain 앞에 선언
     ⚠️ DelegatingFilterProxy 방식: UserDetailsService 생성자 주입이 있어 직접 인스턴스화 불가.
        context-security.xml의 egovSpringSecurityLoginFilter Bean과 연결됨 -->
<filter>
    <filter-name>egovSpringSecurityLoginFilter</filter-name>
    <filter-class>
        org.springframework.web.filter.DelegatingFilterProxy
    </filter-class>
    <init-param>
        <param-name>targetBeanName</param-name>
        <param-value>egovSpringSecurityLoginFilter</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>egovSpringSecurityLoginFilter</filter-name>
    <url-pattern>*.do</url-pattern>
</filter-mapping>

<!-- ⑤ springSecurityFilterChain: Spring Security 전체 체인 (DelegatingFilterProxy) -->
<filter>
    <filter-name>springSecurityFilterChain</filter-name>
    <filter-class>
        org.springframework.web.filter.DelegatingFilterProxy
    </filter-class>
</filter>
<filter-mapping>
    <filter-name>springSecurityFilterChain</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>

<!-- ⑥ EgovSpringSecurityLogoutFilter: 세션 초기화 후 Spring Security 로그아웃 위임 -->
<filter>
    <filter-name>egovSpringSecurityLogoutFilter</filter-name>
    <filter-class>
        ${packageName}.sec.filter.EgovSpringSecurityLogoutFilter
    </filter-class>
</filter>
<filter-mapping>
    <filter-name>egovSpringSecurityLogoutFilter</filter-name>
    <url-pattern>/uat/uia/actionLogout.do</url-pattern>
</filter-mapping>

<!--
⚠️ context-security.xml 로드 안내
   기존 web.xml의 contextConfigLocation <param-value>에 아래 경로를 추가하세요.
   <context-param>을 새로 추가하면 중복 선언으로 Tomcat이 두 번째 선언을 무시합니다.

   기존 contextConfigLocation에 병합할 경로:
     classpath*:egovframework/spring/context-security.xml
     classpath*:egovframework/spring/context-egovuserdetailshelper.xml

   예시 (기존 <param-value>에 한 줄 추가):
   <context-param>
       <param-name>contextConfigLocation</param-name>
       <param-value>
           classpath*:egovframework/spring/context-*.xml
       </param-value>
   </context-param>
-->
