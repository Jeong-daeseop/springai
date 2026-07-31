package egovframework.let.bbs.service.impl;

import egovframework.let.bbs.service.BbsService;
import egovframework.let.bbs.service.BbsVO;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * BBS ServiceImpl
 */
@Service("bbsService")
public class EgovBbsServiceImpl extends EgovAbstractServiceImpl implements BbsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EgovBbsServiceImpl.class);

    @Resource(name = "bbsMapper")
    private BbsMapper bbsMapper;


    @Override
    @Transactional(readOnly = true)
    public List<BbsVO> selectBbsList(BbsVO vo) throws Exception {
        return bbsMapper.selectBbsList(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public int selectBbsListTotCnt(BbsVO vo) throws Exception {
        return bbsMapper.selectBbsListTotCnt(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public BbsVO selectBbs(BbsVO vo) throws Exception {
        return bbsMapper.selectBbs(vo);
    }

    @Override
    @Transactional
    public void updateBbsReadCount(BbsVO vo) throws Exception {
        bbsMapper.updateReadCount(vo);
    }

    @Override
    @Transactional
    public void insertBbs(BbsVO vo) throws Exception {
        LOGGER.debug("insertBbs: {}", vo);
        vo.setNttId(bbsMapper.selectNextBbsNttId());
        bbsMapper.insertBbs(vo);
    }

    @Override
    @Transactional
    public void updateBbs(BbsVO vo) throws Exception {
        bbsMapper.updateBbs(vo);
    }

    @Override
    @Transactional
    public void deleteBbs(BbsVO vo) throws Exception {
        bbsMapper.deleteBbs(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public String selectBoardUseAt(BbsVO vo) throws Exception {
        return bbsMapper.selectBoardUseAt(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public BbsVO selectPrevBbs(BbsVO vo) throws Exception {
        return bbsMapper.selectPrevBbs(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public BbsVO selectNextBbs(BbsVO vo) throws Exception {
        return bbsMapper.selectNextBbs(vo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> selectFileList(String atchFileId) throws Exception {
        if (atchFileId == null) {
            return List.of();
        }
        return bbsMapper.selectFileList(atchFileId);
    }
}
