package ${packageName}.sec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

import java.util.List;
import java.util.Map;

/**
 * LETTNROLES_HIERARCHY 테이블 기반 권한 계층 구조 설정
 *
 * 실제 DB 데이터 (com DB):
 *   PARNTS_ROLE                      CHLDRN_ROLE
 *   IS_AUTHENTICATED_ANONYMOUSLY     IS_AUTHENTICATED_REMEMBERED
 *   IS_AUTHENTICATED_FULLY           ROLE_USER
 *   IS_AUTHENTICATED_REMEMBERED      IS_AUTHENTICATED_FULLY
 *   ROLE_ANONYMOUS                   IS_AUTHENTICATED_ANONYMOUSLY
 *   ROLE_USER                        ROLE_ADMIN
 *
 * → ROLE_USER 가 ROLE_ADMIN 권한 자동 상속
 * → IS_AUTHENTICATED_FULLY(완전인증) > IS_AUTHENTICATED_REMEMBERED > IS_AUTHENTICATED_ANONYMOUSLY
 */
@Configuration
public class EgovRoleHierarchyConfig {

    private final JdbcTemplate jdbcTemplate;

    public EgovRoleHierarchyConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        // LETTNROLES_HIERARCHY 에서 계층 관계 동적 로드
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT PARNTS_ROLE, CHLDRN_ROLE FROM LETTNROLES_HIERARCHY"
        );

        StringBuilder hierarchy = new StringBuilder();
        for (Map<String, Object> row : rows) {
            hierarchy.append(row.get("PARNTS_ROLE"))
                     .append(" > ")
                     .append(row.get("CHLDRN_ROLE"))
                     .append("\n");
        }

        return RoleHierarchyImpl.fromHierarchy(hierarchy.toString());
    }
}
