package ${packageName}.service.impl;

import ${packageName}.service.${domain}VO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ${domainKr} Mapper
 * @author Claude AI
 * @since ${date}
 */
@Mapper
public interface ${domain}Mapper {

    List<${domain}VO> select${domain}List(${domain}VO searchVO);

    int select${domain}ListTotCnt(${domain}VO searchVO);

    ${domain}VO select${domain}(${domain}VO searchVO);

    void insert${domain}(${domain}VO ${domainLc}VO);

    void update${domain}(${domain}VO ${domainLc}VO);

    void delete${domain}(${domain}VO ${domainLc}VO);
<#if designComponentPlan?? && designComponentPlan.commonCodeFields?has_content>
<#list designComponentPlan.commonCodeFields as cc>

    java.util.List<java.util.Map<String, Object>> select${cc.javaName?cap_first}CodeList(
            @org.apache.ibatis.annotations.Param("codeId") String codeId);
</#list>
</#if>
}
