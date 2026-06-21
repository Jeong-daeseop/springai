package ${packageName}.service.impl;

import ${packageName}.service.${domain}Service;
import ${packageName}.service.${domain}VO;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import java.util.List;

/**
 * ${domainKr} ServiceImpl
 */
@Service("${domainLc}Service")
public class Egov${domain}ServiceImpl extends EgovAbstractServiceImpl implements ${domain}Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(Egov${domain}ServiceImpl.class);

    @Resource(name = "${domainLc}Mapper")
    private ${domain}Mapper ${domainLc}Mapper;

    @Resource(name = "egovIdGnrService")
    private EgovIdGnrService egovIdGnrService;

    @Override
    public List<${domain}VO> select${domain}List(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.select${domain}List(vo);
    }

    @Override
    public int select${domain}ListTotCnt(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.select${domain}ListTotCnt(vo);
    }

    @Override
    public ${domain}VO select${domain}(${domain}VO vo) throws Exception {
        ${domain}VO result = ${domainLc}Mapper.select${domain}(vo);
        if (result != null) {
            ${domainLc}Mapper.updateReadCount(vo);
        }
        return result;
    }

    @Override
    public void insert${domain}(${domain}VO vo) throws Exception {
        LOGGER.debug("insert${domain}: {}", vo);
<#if nttId.javaType == "String">
        String nextNttId = egovIdGnrService.getNextStringId();
        vo.set${nttId.javaName?cap_first}(nextNttId);
<#elseif nttId.javaType == "Integer">
        int nextNttId = egovIdGnrService.getNextIntegerId();
        vo.set${nttId.javaName?cap_first}(nextNttId);
<#elseif nttId.javaType == "Long">
        long nextNttId = (long) egovIdGnrService.getNextIntegerId();
        vo.set${nttId.javaName?cap_first}(nextNttId);
<#else>
        String nextNttId = egovIdGnrService.getNextStringId();
        vo.set${nttId.javaName?cap_first}(new java.math.BigDecimal(nextNttId));
</#if>
        ${domainLc}Mapper.insert${domain}(vo);
    }

    @Override
    public void update${domain}(${domain}VO vo) throws Exception {
        ${domainLc}Mapper.update${domain}(vo);
    }

    @Override
    public void delete${domain}(${domain}VO vo) throws Exception {
        ${domainLc}Mapper.delete${domain}(vo);
    }

    @Override
    public String selectBoardUseAt(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.selectBoardUseAt(vo);
    }
}
