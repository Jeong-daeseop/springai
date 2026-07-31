package egovframework.let.emp.service;

import java.util.List;

/**
 * EMPLYRINFO Service 인터페이스
 * @author Claude AI
 * @since GENERATED_DATE
 */
public interface EmployerService {

    List<EmployerVO> selectEmployerList(EmployerVO searchVO) throws Exception;

    int selectEmployerListTotCnt(EmployerVO searchVO) throws Exception;

    EmployerVO selectEmployer(EmployerVO searchVO) throws Exception;

    void insertEmployer(EmployerVO employerVO) throws Exception;

    void updateEmployer(EmployerVO employerVO) throws Exception;

    void deleteEmployer(EmployerVO employerVO) throws Exception;
}
