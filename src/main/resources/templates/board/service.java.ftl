package ${packageName}.service;

import java.util.List;
import java.util.Map;

/**
 * ${domainKr} Service
 */
public interface ${domain}Service {
    List<${domain}VO> select${domain}List(${domain}VO vo) throws Exception;
    int select${domain}ListTotCnt(${domain}VO vo) throws Exception;
    ${domain}VO select${domain}(${domain}VO vo) throws Exception;
    void update${domain}ReadCount(${domain}VO vo) throws Exception;
    void insert${domain}(${domain}VO vo) throws Exception;
    void update${domain}(${domain}VO vo) throws Exception;
    void delete${domain}(${domain}VO vo) throws Exception;
<#if useTableName??>
    String selectBoardUseAt(${domain}VO vo) throws Exception;
</#if>
    ${domain}VO selectPrev${domain}(${domain}VO vo) throws Exception;
    ${domain}VO selectNext${domain}(${domain}VO vo) throws Exception;
<#if hasFile && fileDetailTableName??>
    List<Map<String, Object>> selectFileList(${atchFileId.javaType} atchFileId) throws Exception;
</#if>
}
