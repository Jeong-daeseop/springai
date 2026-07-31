package egovframework.let.emp.service.impl;

import egovframework.let.emp.service.EmployerService;
import egovframework.let.emp.service.EmployerVO;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EMPLYRINFO ServiceImpl
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Service("employerService")
@RequiredArgsConstructor
public class EgovEmployerServiceImpl extends EgovAbstractServiceImpl
        implements EmployerService {

    private final EmployerMapper employerMapper;

    @Override
    public List<EmployerVO> selectEmployerList(EmployerVO searchVO) throws Exception {
        return employerMapper.selectEmployerList(searchVO);
    }

    @Override
    public int selectEmployerListTotCnt(EmployerVO searchVO) throws Exception {
        return employerMapper.selectEmployerListTotCnt(searchVO);
    }

    @Override
    public EmployerVO selectEmployer(EmployerVO searchVO) throws Exception {
        return employerMapper.selectEmployer(searchVO);
    }

    @Override
    @Transactional
    public void insertEmployer(EmployerVO employerVO) throws Exception {
        employerMapper.insertEmployer(employerVO);
    }

    @Override
    @Transactional
    public void updateEmployer(EmployerVO employerVO) throws Exception {
        employerMapper.updateEmployer(employerVO);
    }

    @Override
    @Transactional
    public void deleteEmployer(EmployerVO employerVO) throws Exception {
        employerMapper.deleteEmployer(employerVO);
    }
}
