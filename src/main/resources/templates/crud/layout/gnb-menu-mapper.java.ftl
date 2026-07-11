package ${packageName}.cmm.service;

import ${packageName}.cmm.vo.GnbMenuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * GNB 동적 메뉴 조회 Mapper
 * @author Claude AI
 * @since ${date}
 */
@Mapper
public interface GnbMenuMapper {

    /**
     * 상위 메뉴 번호(UPPER_MENU_NO) 기준 자식 메뉴 목록을 조회한다.
     * GNB는 upperMenuNo=0(최상위)으로 호출한다.
     */
    List<GnbMenuVO> selectGnbMenuList(@Param("upperMenuNo") Long upperMenuNo);
}
