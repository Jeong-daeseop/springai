package com.krdevops.springai.mapper;

import com.krdevops.springai.vo.EmployeeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EmployeeRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EmployeeVO> rowMapper = (rs, rowNum) -> {
        EmployeeVO vo = new EmployeeVO();
        vo.setEmplyrId(rs.getString("EMPLYR_ID"));
        vo.setUserNm(rs.getString("USER_NM"));
        vo.setEmailAdres(rs.getString("EMAIL_ADRES"));
        vo.setOfcpsNm(rs.getString("OFCPS_NM"));
        vo.setMbtlnum(rs.getString("MBTLNUM"));
        vo.setEmplyrySttuscode(rs.getString("EMPLYR_STTUS_CODE"));
        vo.setEsntlId(rs.getString("ESNTL_ID"));
        return vo;
    };

    public List<EmployeeVO> selectEmployeeList(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return jdbcTemplate.query(
                "SELECT EMPLYR_ID, USER_NM, EMAIL_ADRES, OFCPS_NM, MBTLNUM, EMPLYR_STTUS_CODE, ESNTL_ID " +
                "FROM COMTNEMPLYRINFO ORDER BY USER_NM LIMIT 20",
                rowMapper);
        }
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(
            "SELECT EMPLYR_ID, USER_NM, EMAIL_ADRES, OFCPS_NM, MBTLNUM, EMPLYR_STTUS_CODE, ESNTL_ID " +
            "FROM COMTNEMPLYRINFO " +
            "WHERE USER_NM LIKE ? OR EMAIL_ADRES LIKE ? OR OFCPS_NM LIKE ? " +
            "ORDER BY USER_NM LIMIT 20",
            rowMapper, like, like, like);
    }

    public EmployeeVO selectEmployee(String emplyrId) {
        List<EmployeeVO> list = jdbcTemplate.query(
            "SELECT EMPLYR_ID, USER_NM, EMAIL_ADRES, OFCPS_NM, MBTLNUM, EMPLYR_STTUS_CODE, ESNTL_ID " +
            "FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?",
            rowMapper, emplyrId);
        return list.isEmpty() ? null : list.get(0);
    }

    public int insertEmployee(EmployeeVO vo) {
        return jdbcTemplate.update(
            "INSERT INTO COMTNEMPLYRINFO " +
            "(EMPLYR_ID, USER_NM, EMAIL_ADRES, OFCPS_NM, MBTLNUM, EMPLYR_STTUS_CODE, ESNTL_ID, " +
            " PASSWORD, HOUSE_ADRES, PASSWORD_HINT, PASSWORD_CNSR, HOUSE_END_TELNO, AREA_NO, ZIP) " +
            "VALUES (?, ?, ?, ?, ?, 'A', ?, 'temp1234!', '', '', '', '', '', '')",
            vo.getEmplyrId(), vo.getUserNm(), vo.getEmailAdres(),
            vo.getOfcpsNm(), vo.getMbtlnum(), vo.getEsntlId());
    }

    public int updateEmployee(EmployeeVO vo) {
        StringBuilder sql = new StringBuilder("UPDATE COMTNEMPLYRINFO SET ");
        var params = new java.util.ArrayList<>();

        if (vo.getUserNm() != null)     { sql.append("USER_NM=?, ");     params.add(vo.getUserNm()); }
        if (vo.getEmailAdres() != null) { sql.append("EMAIL_ADRES=?, "); params.add(vo.getEmailAdres()); }
        if (vo.getOfcpsNm() != null)    { sql.append("OFCPS_NM=?, ");    params.add(vo.getOfcpsNm()); }
        if (vo.getMbtlnum() != null)    { sql.append("MBTLNUM=?, ");     params.add(vo.getMbtlnum()); }

        if (params.isEmpty()) return 0;

        String query = sql.toString().replaceAll(", $", "") + " WHERE EMPLYR_ID=?";
        params.add(vo.getEmplyrId());
        return jdbcTemplate.update(query, params.toArray());
    }

    public int deleteEmployee(String emplyrId) {
        return jdbcTemplate.update("DELETE FROM COMTNEMPLYRINFO WHERE EMPLYR_ID=?", emplyrId);
    }
}
