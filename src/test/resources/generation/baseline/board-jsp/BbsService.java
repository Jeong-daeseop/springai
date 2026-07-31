package egovframework.let.bbs.service;

import java.util.List;
import java.util.Map;

/**
 * BBS Service
 */
public interface BbsService {
    List<BbsVO> selectBbsList(BbsVO vo) throws Exception;
    int selectBbsListTotCnt(BbsVO vo) throws Exception;
    BbsVO selectBbs(BbsVO vo) throws Exception;
    void updateBbsReadCount(BbsVO vo) throws Exception;
    void insertBbs(BbsVO vo) throws Exception;
    void updateBbs(BbsVO vo) throws Exception;
    void deleteBbs(BbsVO vo) throws Exception;
    String selectBoardUseAt(BbsVO vo) throws Exception;
    BbsVO selectPrevBbs(BbsVO vo) throws Exception;
    BbsVO selectNextBbs(BbsVO vo) throws Exception;
    List<Map<String, Object>> selectFileList(String atchFileId) throws Exception;
}
