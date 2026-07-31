package egovframework.let.bbs.service.impl;

import egovframework.let.bbs.service.BbsVO;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * BBS Mapper
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Mapper
public interface BbsMapper {

    /** BBS 목록 조회 */
    List<BbsVO> selectBbsList(BbsVO searchVO);

    /** BBS 목록 건수 */
    int selectBbsListTotCnt(BbsVO searchVO);

    /** BBS 단건 조회 */
    BbsVO selectBbs(BbsVO vo);

    /** BBS 등록 */
    void insertBbs(BbsVO vo);

    /** 숫자형 게시물 ID 채번 — insert 트랜잭션에서 호출 */
    Long selectNextBbsNttId();

    /** BBS 수정 */
    void updateBbs(BbsVO vo);

    /** BBS 논리삭제 */
    void deleteBbs(BbsVO vo);

    /** 조회수 증가 */
    void updateReadCount(BbsVO vo);

    /** 게시판 사용 여부 조회 */
    String selectBoardUseAt(BbsVO vo);

    /** 이전 게시글 조회 */
    BbsVO selectPrevBbs(BbsVO vo);

    /** 다음 게시글 조회 */
    BbsVO selectNextBbs(BbsVO vo);

    /** 첨부파일 상세 목록 */
    List<Map<String, Object>> selectFileList(@Param("atchFileId") String atchFileId);
}
