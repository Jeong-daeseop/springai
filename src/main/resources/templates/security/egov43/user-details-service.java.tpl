package ${packageName}.sec.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * eGovFrame 4.3 표준 UserDetailsService 구현체
 *
 * 사용자 조회: LETTNEMPLYRINFO (EMPLYR_ID, PASSWORD, LOCK_AT, EMPLYR_STTUS_CODE)
 * 권한 조회:  LETTNEMPLYRSCRTYESTBS → AUTHOR_CODE (ROLE_ADMIN, ROLE_USER 등)
 *
 * EMPLYR_STTUS_CODE = 'ESC01' → 재직중 사용자만 인증
 *
 * ⚠️ eGovFrame 5.0은 EgovJdbcUserDetailsManager(RTE 자동 구성)가 대체
 *    5.0에서는 context-security.xml jdbcUsersByUsernameQuery 프로퍼티로 설정
 */
@Service
public class EgovUserDetailsServiceImpl implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public EgovUserDetailsServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        // 1. LETTNEMPLYRINFO 에서 사용자 조회 (재직중만)
        List<java.util.Map<String, Object>> users = jdbcTemplate.queryForList(
            "SELECT EMPLYR_ID, PASSWORD, LOCK_AT " +
            "FROM LETTNEMPLYRINFO " +
            "WHERE EMPLYR_ID = ? AND EMPLYR_STTUS_CODE = 'ESC01'",
            username
        );

        if (users.isEmpty()) {
            throw new UsernameNotFoundException(
                "사용자를 찾을 수 없습니다: " + username);
        }

        java.util.Map<String, Object> user = users.get(0);
        String  password = (String) user.get("PASSWORD");
        boolean locked   = "Y".equals(user.get("LOCK_AT"));

        // 2. LETTNEMPLYRSCRTYESTBS 에서 권한(AUTHOR_CODE) 조회
        List<GrantedAuthority> authorities = jdbcTemplate.query(
            "SELECT AUTHOR_CODE " +
            "FROM LETTNEMPLYRSCRTYESTBS " +
            "WHERE SCRTY_DTRMN_TRGET_ID = ?",
            (rs, rowNum) ->
                new SimpleGrantedAuthority(rs.getString("AUTHOR_CODE")),
            username
        );

        if (authorities.isEmpty()) {
            // 권한 미설정 시 기본 ROLE_USER 부여
            authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return User.builder()
            .username(username)
            .password(password)
            .authorities(authorities)
            .accountLocked(locked)
            .build();
    }
}
