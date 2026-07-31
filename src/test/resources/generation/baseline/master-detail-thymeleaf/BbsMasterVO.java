package egovframework.let.bbs.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

/**
 * BBSMASTER VO
 * 테이블: LETTNBBSMASTER
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Getter
@Setter
public class BbsMasterVO {

    // 게시판ID
    @Size(max = 20)
    private String bbsId;

    // 게시판명
    @NotBlank
    @Size(max = 100)
    private String bbsNm;

    // 게시판소개
    @Size(max = 2000)
    private String bbsIntrcn;

    // 페이징/검색 공통 필드
    private int pageIndex = 1;
    private int pageUnit = 10;
    private int pageSize = 10;
    private int firstIndex = 0;
    private int lastIndex = 0;
    private int recordCountPerPage = 10;
    private String searchCondition = "";
    private String searchKeyword = "";
    private PaginationInfo paginationInfo;
}
