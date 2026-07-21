package ${packageName}.service.impl;

import ${packageName}.service.${domain}VO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    int delete${domain}Bulk(@Param("ids") List<${pk.javaType}> ids);
}
