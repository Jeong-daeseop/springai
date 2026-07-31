package egovframework.let.bbs.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

/**
 * BBSUSE VO
 * 테이블: LETTNBBSUSE
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Getter
@Setter
public class BbsuseVO {

    // 게시판ID
    @Size(max = 20)
    private String bbsId;

    // 사용여부
    @NotBlank
    @Size(max = 1)
    private String useAt;

    // 전송대상분류
    @Size(max = 20)
    private String sendTargetClassify;

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
