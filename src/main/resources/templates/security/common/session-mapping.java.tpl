package ${packageName}.uat.uia.service.impl;

import org.egovframe.rte.fdl.security.userdetails.EgovUserDetails;
import org.egovframe.rte.fdl.security.userdetails.EgovUsersByUsernameMapping;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * EgovSessionMapping — DB ResultSet → EgovUserDetails 변환
 *
 * context-security.xml EgovSecurityConfig Bean의 jdbcMapClass 프로퍼티에서 참조:
 *   <property name="jdbcMapClass" value="${packageName}.uat.uia.service.impl.EgovSessionMapping"/>
 *
 * DB 컬럼 → EgovUserDetails 매핑:
 *   USER_ID   → userId (인증 주체)
 *   PASSWORD  → password (해시 저장값)
 *   (나머지 컬럼은 loginVO에 담아 4번째 인자로 전달)
 *
 * ⚠️ 프로젝트 사용자 테이블 컬럼명에 맞게 수정 필요
 *    COM계열: LETTNEMPLYRINFO (USER_ID, PASSWORD, USER_NM, DEPT_ID)
 * ⚠️ loginVO는 프로젝트 LoginVO 클래스로 교체 후 주석 해제
 */
public class EgovSessionMapping extends EgovUsersByUsernameMapping {

    public EgovSessionMapping(DataSource dataSource, String sql) {
        super(dataSource, sql);
    }

    @Override
    public EgovUserDetails mapRow(ResultSet rs, int rownum) throws SQLException {
        String userId   = rs.getString("USER_ID");   // ⚠️ 컬럼명 확인
        String password = rs.getString("PASSWORD");  // ⚠️ 컬럼명 확인

        // ⚠️ LoginVO 구성 — 프로젝트 VO 클래스로 교체
        // LoginVO loginVO = new LoginVO();
        // loginVO.setId(userId);
        // loginVO.setPassword(password);
        // loginVO.setName(rs.getString("USER_NM"));
        // loginVO.setOrgnztId(rs.getString("DEPT_ID"));

        // EgovUserDetails(username, password, enabled, loginVO)
        // ⚠️ 마지막 인자(null)를 프로젝트 loginVO 인스턴스로 교체
        return new EgovUserDetails(userId, password, true, null);
    }
}
