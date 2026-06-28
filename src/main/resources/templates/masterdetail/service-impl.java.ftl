package ${packageName}.service.impl;

import ${packageName}.service.${detail.domain}VO;
import ${packageName}.service.${master.domain}Service;
import ${packageName}.service.${master.domain}VO;
import lombok.RequiredArgsConstructor;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ${master.domainKr} ServiceImpl
 * @author Claude AI
 * @since ${date}
 */
@Service("${master.domainLc}Service")
@RequiredArgsConstructor
public class Egov${master.domain}ServiceImpl extends EgovAbstractServiceImpl
        implements ${master.domain}Service {

    private final ${master.domain}Mapper ${master.domainLc}Mapper;
    private final ${detail.domain}Mapper ${detail.domainLc}Mapper;

    @Override
    public List<${master.domain}VO> select${master.domain}List(${master.domain}VO searchVO) throws Exception {
        return ${master.domainLc}Mapper.select${master.domain}List(searchVO);
    }

    @Override
    public int select${master.domain}ListTotCnt(${master.domain}VO searchVO) throws Exception {
        return ${master.domainLc}Mapper.select${master.domain}ListTotCnt(searchVO);
    }

    @Override
    public ${master.domain}VO select${master.domain}(${master.domain}VO searchVO) throws Exception {
        return ${master.domainLc}Mapper.select${master.domain}(searchVO);
    }

    @Override
    @Transactional
    public void insert${master.domain}(${master.domain}VO ${master.domainLc}VO) throws Exception {
        ${master.domainLc}Mapper.insert${master.domain}(${master.domainLc}VO);
    }

    @Override
    @Transactional
    public void update${master.domain}(${master.domain}VO ${master.domainLc}VO) throws Exception {
        ${master.domainLc}Mapper.update${master.domain}(${master.domainLc}VO);
    }

    @Override
    @Transactional
    public void delete${master.domain}(${master.domain}VO ${master.domainLc}VO) throws Exception {
        ${master.domainLc}Mapper.delete${master.domain}(${master.domainLc}VO);
    }

    @Override
    public List<${detail.domain}VO> select${detail.domain}List(String ${fkField}) throws Exception {
        return ${detail.domainLc}Mapper.select${detail.domain}List(${fkField});
    }
}
