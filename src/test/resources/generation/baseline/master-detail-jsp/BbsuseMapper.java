package egovframework.let.bbs.service.impl;

import egovframework.let.bbs.service.BbsuseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * BBSUSE Mapper
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Mapper
public interface BbsuseMapper {

    List<BbsuseVO> selectBbsuseList(@Param("bbsId") String bbsId);

    BbsuseVO selectBbsuse(BbsuseVO bbsuseVO);

    void insertBbsuse(BbsuseVO bbsuseVO);

    void updateBbsuse(BbsuseVO bbsuseVO);

    void deleteBbsuse(BbsuseVO bbsuseVO);

    int deleteBbsuseByMasterIds(@Param("ids") List<String> ids);
}
