package ${packageName}.service.impl;

import ${packageName}.service.${domain}VO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

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

    /** ${domainKr} 수정 */
    void update${domain}(${domain}VO vo);

    /** ${domainKr} 논리삭제 */
    void delete${domain}(${domain}VO vo);

    /** 조회수 증가 */
    void updateReadCount(${domain}VO vo);

    /** 게시판 사용 여부 조회 */
    String selectBoardUseAt(${domain}VO vo);

    /** 이전 게시글 조회 */
    ${domain}VO selectPrev${domain}(${domain}VO vo);

    /** 다음 게시글 조회 */
    ${domain}VO selectNext${domain}(${domain}VO vo);
}
