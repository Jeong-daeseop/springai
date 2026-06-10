package ${packageName}.config;

import org.egovframe.rte.fdl.security.config.EgovSecurityConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * eGovFrame 5.0 Spring Security Java Config 진입점
 *
 * 3계층 구조:
 *   1. context-security.xml → EgovSecurityConfig Bean (설정값 POJO)
 *   2. 이 클래스            → @Import 진입점 (내용 없음)
 *   3. EgovSecurityConfiguration → RTE, SecurityFilterChain 자동 구성
 *
 * EgovSecurityConfiguration이 자동 구성하는 항목:
 *   - SecurityFilterChain (URL 접근제어 / 세션 / 헤더 / CSRF)
 *   - AuthenticationManager (DaoAuthenticationProvider + RoleHierarchyAuthoritiesMapper)
 *   - EgovJdbcUserDetailsManager (사용자/권한 SQL 조회)
 *   - EgovMultipleRoleAuthorizationManager (DB URL 권한 동적 로드)
 *
 * ⚠️ EgovSecurityConfiguration을 XML <bean>으로 직접 선언하면
 *    Spring Security 6.5 + Java 17 환경에서 BootstrapMethodError 발생.
 *    반드시 @Import 방식으로 로드해야 함.
 *
 * ⚠️ context-security.xml의 EgovSecurityConfig Bean과 반드시 함께 사용.
 *    (javaConfig 단독 사용 불가 — EgovSecurityConfig Bean 없으면 NullPointerException)
 */
@Configuration
@Import(EgovSecurityConfiguration.class)
public class EgovProjectSecurityConfig {
    // 내용 없음 — 진입점 역할만
    // SecurityFilterChain 등 모든 보안 설정은 EgovSecurityConfiguration(RTE)이 담당
}
