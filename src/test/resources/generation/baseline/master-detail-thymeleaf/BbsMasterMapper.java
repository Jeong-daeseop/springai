package egovframework.let.bbs.service.impl;

import egovframework.let.bbs.service.BbsMasterVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * BBSMASTER Mapper
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Mapper
public interface BbsMasterMapper {

    List<BbsMasterVO> selectBbsMasterList(BbsMasterVO searchVO);

    int selectBbsMasterListTotCnt(BbsMasterVO searchVO);

    BbsMasterVO selectBbsMaster(BbsMasterVO searchVO);

    void insertBbsMaster(BbsMasterVO bbsMasterVO);

    void updateBbsMaster(BbsMasterVO bbsMasterVO);

    void deleteBbsMaster(BbsMasterVO bbsMasterVO);

    int deleteBbsMasterBulk(@Param("ids") List<String> ids);
}
