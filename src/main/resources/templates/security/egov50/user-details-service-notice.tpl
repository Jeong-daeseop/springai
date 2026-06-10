⚠️ eGovFrame 5.0에서는 EgovUserDetailsServiceImpl이 필요하지 않습니다.

[5.0 대체 방식]
RTE EgovSecurityConfiguration이 EgovJdbcUserDetailsManager를 자동 구성합니다.
사용자/권한 조회 SQL은 context-security.xml의 EgovSecurityConfig Bean 프로퍼티로 설정합니다.

  <bean id="securityConfig" class="org.egovframe.rte.fdl.security.config.EgovSecurityConfig">
      <!-- 사용자 조회 SQL: USER_ID, PASSWORD, ENABLED 컬럼 필수 -->
      <property name="jdbcUsersByUsernameQuery"
          value="SELECT USER_ID, PASSWORD, 1 ENABLED FROM TN_USERS WHERE USER_ID = ?"/>
      <!-- 권한 조회 SQL: USER_ID, AUTHORITY 컬럼 필수 -->
      <property name="jdbcAuthoritiesByUsernameQuery"
          value="SELECT USER_ID, AUTHOR_CODE AUTHORITY
                 FROM TN_EMPLYRSCRTYESTBS WHERE SCRTY_DTRMN_TRGET_ID = ?"/>
      <!-- DB ResultSet → LoginVO → EgovUserDetails 변환 클래스 -->
      <property name="jdbcMapClass" value="egovframework.bopr.security.EgovSessionMapping"/>
  </bean>

[필요한 클래스]
- EgovSessionMapping → getSecurityTemplate("sessionmapping", packageName, "5.0")
- context-security.xml 전체 → getSecurityTemplate("contextSecurity", packageName, "5.0")
