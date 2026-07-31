package egovframework.let.emp.service.impl;

import egovframework.let.emp.service.EmployerVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * EMPLYRINFO Mapper
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Mapper
public interface EmployerMapper {

    List<EmployerVO> selectEmployerList(EmployerVO searchVO);

    int selectEmployerListTotCnt(EmployerVO searchVO);

    EmployerVO selectEmployer(EmployerVO searchVO);

    void insertEmployer(EmployerVO employerVO);

    void updateEmployer(EmployerVO employerVO);

    void deleteEmployer(EmployerVO employerVO);
}
