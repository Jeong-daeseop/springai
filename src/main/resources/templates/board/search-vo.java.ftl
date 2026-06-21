package ${packageName}.service;

import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * ${domainKr} 검색 VO
 * @author Claude AI
 * @since ${date}
 */
@Getter
@Setter
public class ${domain}SearchVO {

    private static final long serialVersionUID = 1L;

    /** 게시판 ID */
    private String bbsId;

    /** 검색 키워드 */
    private String searchKeyword;

    /** 검색 조건 (1:제목, 2:내용, 3:작성자) */
    private String searchCondition;

    /** 페이지 번호 */
    private int pageIndex = 1;

    /** 페이지당 건수 */
    private int pageUnit = 10;

    /** 페이지 사이즈 */
    private int pageSize = 10;

    /** 페이지네이션 정보 */
    private PaginationInfo paginationInfo;

    public void setPaginationInfo(PaginationInfo paginationInfo) { this.paginationInfo = paginationInfo; }
    public PaginationInfo getPaginationInfo() { return paginationInfo; }
    public void setPageUnit(int pageUnit) { this.pageUnit = pageUnit; }
    public int getPageUnit() { return pageUnit; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getPageSize() { return pageSize; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
    public int getPageIndex() { return pageIndex; }
}
