package egovframework.let.bbs.service.impl;

import egovframework.let.bbs.service.BbsuseVO;
import egovframework.let.bbs.service.BbsMasterService;
import egovframework.let.bbs.service.BbsMasterVO;
import lombok.RequiredArgsConstructor;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BBSMASTER ServiceImpl
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Service("bbsMasterService")
@RequiredArgsConstructor
public class EgovBbsMasterServiceImpl extends EgovAbstractServiceImpl
        implements BbsMasterService {

    private final BbsMasterMapper bbsMasterMapper;
    private final BbsuseMapper bbsuseMapper;

    @Override
    public List<BbsMasterVO> selectBbsMasterList(BbsMasterVO searchVO) throws Exception {
        return bbsMasterMapper.selectBbsMasterList(searchVO);
    }

    @Override
    public int selectBbsMasterListTotCnt(BbsMasterVO searchVO) throws Exception {
        return bbsMasterMapper.selectBbsMasterListTotCnt(searchVO);
    }

    @Override
    public BbsMasterVO selectBbsMaster(BbsMasterVO searchVO) throws Exception {
        return bbsMasterMapper.selectBbsMaster(searchVO);
    }

    @Override
    @Transactional
    public void insertBbsMaster(BbsMasterVO bbsMasterVO) throws Exception {
        bbsMasterMapper.insertBbsMaster(bbsMasterVO);
    }

    @Override
    @Transactional
    public void updateBbsMaster(BbsMasterVO bbsMasterVO) throws Exception {
        bbsMasterMapper.updateBbsMaster(bbsMasterVO);
    }

    @Override
    @Transactional
    public void deleteBbsMaster(BbsMasterVO bbsMasterVO) throws Exception {
        bbsMasterMapper.deleteBbsMaster(bbsMasterVO);
    }

    @Override
    @Transactional
    public int deleteBbsMasterBulk(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        if (ids.size() > 1000) {
            throw new IllegalArgumentException("일괄 삭제는 한 번에 최대 1000건까지 가능합니다.");
        }
        bbsuseMapper.deleteBbsuseByMasterIds(ids);
        return bbsMasterMapper.deleteBbsMasterBulk(ids);
    }

    @Override
    public List<BbsuseVO> selectBbsuseList(String bbsId) throws Exception {
        return bbsuseMapper.selectBbsuseList(bbsId);
    }
}
