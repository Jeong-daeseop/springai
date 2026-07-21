package ${packageName}.service.impl;

import ${packageName}.service.${domain}VO;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ${domainKr} Mapper
 * @author Claude AI
 * @since ${date}
 */
@Mapper
public interface ${domain}Mapper {

    /** ${domainKr} 목록 조회 */
    List<${domain}VO> select${domain}List(${domain}VO searchVO);

    /** ${domainKr} 목록 건수 */
    int select${domain}ListTotCnt(${domain}VO searchVO);

    /** ${domainKr} 단건 조회 */
    ${domain}VO select${domain}(${domain}VO vo);

    /** ${domainKr} 등록 */
    void insert${domain}(${domain}VO vo);

<#if nttId.javaType != "String">
    /** 숫자형 게시물 ID 채번 — insert 트랜잭션에서 호출 */
    ${nttId.javaType} selectNext${domain}NttId();
</#if>

    /** ${domainKr} 수정 */
    void update${domain}(${domain}VO vo);

    /** ${domainKr} 논리삭제 */
    void delete${domain}(${domain}VO vo);

    /** 조회수 증가 */
    void updateReadCount(${domain}VO vo);

<#if useTableName??>
    /** 게시판 사용 여부 조회 */
    String selectBoardUseAt(${domain}VO vo);
</#if>

    /** 이전 게시글 조회 */
    ${domain}VO selectPrev${domain}(${domain}VO vo);

    /** 다음 게시글 조회 */
    ${domain}VO selectNext${domain}(${domain}VO vo);

<#if hasFile && fileDetailTableName??>
    /** 첨부파일 상세 목록 */
    List<Map<String, Object>> selectFileList(@Param("atchFileId") ${atchFileId.javaType} atchFileId);
</#if>
}
