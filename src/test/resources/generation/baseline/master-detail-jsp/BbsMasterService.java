package egovframework.let.bbs.service;

import java.util.List;

/**
 * BBSMASTER Service 인터페이스
 * @author Claude AI
 * @since GENERATED_DATE
 */
public interface BbsMasterService {

    List<BbsMasterVO> selectBbsMasterList(BbsMasterVO searchVO) throws Exception;

    int selectBbsMasterListTotCnt(BbsMasterVO searchVO) throws Exception;

    BbsMasterVO selectBbsMaster(BbsMasterVO searchVO) throws Exception;

    void insertBbsMaster(BbsMasterVO bbsMasterVO) throws Exception;

    void updateBbsMaster(BbsMasterVO bbsMasterVO) throws Exception;

    void deleteBbsMaster(BbsMasterVO bbsMasterVO) throws Exception;

    int deleteBbsMasterBulk(List<String> ids) throws Exception;

    List<BbsuseVO> selectBbsuseList(String bbsId) throws Exception;
}
