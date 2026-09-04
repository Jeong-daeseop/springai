package ${packageName}.service;

import java.util.List;

/**
 * ${domainKr} Service 인터페이스
 * @author Claude AI
 * @since ${date}
 */
public interface ${domain}Service {

    List<${domain}VO> select${domain}List(${domain}VO searchVO) throws Exception;

    int select${domain}ListTotCnt(${domain}VO searchVO) throws Exception;

    ${domain}VO select${domain}(${domain}VO searchVO) throws Exception;

    void insert${domain}(${domain}VO ${domainLc}VO) throws Exception;

    void update${domain}(${domain}VO ${domainLc}VO) throws Exception;

    void delete${domain}(${domain}VO ${domainLc}VO) throws Exception;
<#if designComponentPlan?? && designComponentPlan.commonCodeFields?has_content>
<#list designComponentPlan.commonCodeFields as cc>

    /** ${cc.javaName} 공통코드 select 목록 (디자인 참조 화면 전용) */
    java.util.List<java.util.Map<String, Object>> select${cc.javaName?cap_first}CodeList(String codeId) throws Exception;
</#list>
</#if>
}
