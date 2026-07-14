package ${packageName}.cmm.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * GNB 동적 메뉴 조회 결과 VO (${menuTableName!"LETTNMENUINFO"} + ${programTableName!"LETTNPROGRMLIST"} 조인 결과)
 * @author Claude AI
 * @since ${date}
 */
@Getter
@Setter
public class GnbMenuVO {

    private Long menuNo;
    private Long upperMenuNo;
    private String menuNm;
    private Integer menuOrdr;
    private String progrmFileNm;
    private String progrmKoreanNm;
    private String progrmStrePath;
    private String url;
    private List<GnbMenuVO> children = new ArrayList<>();
}
