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
        // @region:protected:beforeInsert start
        // 저장 전 커스텀 검증/가공 로직을 이 안에 작성하면 재생성 시 보존됩니다.
        // @region:protected:beforeInsert end
        employerMapper.insertEmployer(employerVO);
    }

    @Override
    @Transactional
    public void updateEmployer(EmployerVO employerVO) throws Exception {
        // @region:protected:beforeUpdate start
        // 수정 전 커스텀 검증/가공 로직을 이 안에 작성하면 재생성 시 보존됩니다.
        // @region:protected:beforeUpdate end
        employerMapper.updateEmployer(employerVO);
    }

    @Override
    @Transactional
    public void deleteEmployer(EmployerVO employerVO) throws Exception {
        // @region:protected:beforeDelete start
        // 삭제 전 커스텀 검증/가공 로직을 이 안에 작성하면 재생성 시 보존됩니다.
        // @region:protected:beforeDelete end
        employerMapper.deleteEmployer(employerVO);
    }
}
