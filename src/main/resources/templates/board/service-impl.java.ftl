package ${packageName}.service.impl;

import ${packageName}.service.${domain}Service;
import ${packageName}.service.${domain}VO;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
<#if nttId.javaType == "String">
import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
</#if>
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * ${domainKr} ServiceImpl
 */
@Service("${domainLc}Service")
public class Egov${domain}ServiceImpl extends EgovAbstractServiceImpl implements ${domain}Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(Egov${domain}ServiceImpl.class);

    @Resource(name = "${domainLc}Mapper")
    private ${domain}Mapper ${domainLc}Mapper;

<#if nttId.javaType == "String">
    @Resource(name = "egovIdGnrService")
    private EgovIdGnrService egovIdGnrService;
</#if>

    @Override
    @Transactional(readOnly = true)
    public List<${domain}VO> select${domain}List(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.select${domain}List(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public int select${domain}ListTotCnt(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.select${domain}ListTotCnt(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public ${domain}VO select${domain}(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.select${domain}(vo);
    }

    @Override
    @Transactional
    public void update${domain}ReadCount(${domain}VO vo) throws Exception {
        ${domainLc}Mapper.updateReadCount(vo);
    }

    @Override
    @Transactional
    public void insert${domain}(${domain}VO vo) throws Exception {
        LOGGER.debug("insert${domain}: {}", vo);
<#if nttId.javaType == "String">
        String nextNttId = egovIdGnrService.getNextStringId();
        vo.set${nttId.javaName?cap_first}(nextNttId);
<#else>
        vo.set${nttId.javaName?cap_first}(${domainLc}Mapper.selectNext${domain}NttId());
</#if>
        ${domainLc}Mapper.insert${domain}(vo);
    }

    @Override
    @Transactional
    public void update${domain}(${domain}VO vo) throws Exception {
        ${domainLc}Mapper.update${domain}(vo);
    }

    @Override
    @Transactional
    public void delete${domain}(${domain}VO vo) throws Exception {
        ${domainLc}Mapper.delete${domain}(vo);
    }

<#if useTableName??>
    @Override
    @Transactional(readOnly = true)
    public String selectBoardUseAt(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.selectBoardUseAt(vo);
    }
</#if>

    @Override
    @Transactional(readOnly = true)
    public ${domain}VO selectPrev${domain}(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.selectPrev${domain}(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public ${domain}VO selectNext${domain}(${domain}VO vo) throws Exception {
        return ${domainLc}Mapper.selectNext${domain}(vo);
    }

<#if hasFile && fileDetailTableName??>
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> selectFileList(${atchFileId.javaType} atchFileId) throws Exception {
        if (atchFileId == null) {
            return List.of();
        }
        return ${domainLc}Mapper.selectFileList(atchFileId);
    }
</#if>
}
