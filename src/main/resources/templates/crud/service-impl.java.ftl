package ${packageName}.service.impl;

import ${packageName}.service.${domain}Service;
import ${packageName}.service.${domain}VO;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ${domainKr} ServiceImpl
 * @author Claude AI
 * @since ${date}
 */
@Service("${domainLc}Service")
@RequiredArgsConstructor
public class Egov${domain}ServiceImpl extends EgovAbstractServiceImpl
        implements ${domain}Service {

    private final ${domain}Mapper ${domainLc}Mapper;

    @Override
    public List<${domain}VO> select${domain}List(${domain}VO searchVO) throws Exception {
        return ${domainLc}Mapper.select${domain}List(searchVO);
    }

    @Override
    public int select${domain}ListTotCnt(${domain}VO searchVO) throws Exception {
        return ${domainLc}Mapper.select${domain}ListTotCnt(searchVO);
    }

    @Override
    public ${domain}VO select${domain}(${domain}VO searchVO) throws Exception {
        return ${domainLc}Mapper.select${domain}(searchVO);
    }

    @Override
    @Transactional
    public void insert${domain}(${domain}VO ${domainLc}VO) throws Exception {
        ${domainLc}Mapper.insert${domain}(${domainLc}VO);
    }

    @Override
    @Transactional
    public void update${domain}(${domain}VO ${domainLc}VO) throws Exception {
        ${domainLc}Mapper.update${domain}(${domainLc}VO);
    }

    @Override
    @Transactional
    public void delete${domain}(${domain}VO ${domainLc}VO) throws Exception {
        ${domainLc}Mapper.delete${domain}(${domainLc}VO);
    }
}
