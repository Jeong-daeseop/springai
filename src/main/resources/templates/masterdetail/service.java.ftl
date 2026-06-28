package ${packageName}.service;

import java.util.List;

/**
 * ${master.domainKr} Service 인터페이스
 * @author Claude AI
 * @since ${date}
 */
public interface ${master.domain}Service {

    List<${master.domain}VO> select${master.domain}List(${master.domain}VO searchVO) throws Exception;

    int select${master.domain}ListTotCnt(${master.domain}VO searchVO) throws Exception;

    ${master.domain}VO select${master.domain}(${master.domain}VO searchVO) throws Exception;

    void insert${master.domain}(${master.domain}VO ${master.domainLc}VO) throws Exception;

    void update${master.domain}(${master.domain}VO ${master.domainLc}VO) throws Exception;

    void delete${master.domain}(${master.domain}VO ${master.domainLc}VO) throws Exception;

    List<${detail.domain}VO> select${detail.domain}List(String ${fkField}) throws Exception;
}
