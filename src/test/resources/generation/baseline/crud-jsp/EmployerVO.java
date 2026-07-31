package egovframework.let.emp.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

/**
 * EMPLYRINFO VO
 * 테이블: LETTNEMPLYRINFO
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Getter
@Setter
public class EmployerVO {

    // 직원ID
    @Size(max = 20)
    private String emplyrId;

    // 직원명
    @NotBlank
    @Size(max = 50)
    private String emplyrNm;

    // 이메일주소
    @Size(max = 100)
    private String emailAdres;

    // 직급명
    @Size(max = 50)
    private String ofcpsNm;

    // 최초등록시점
    private String frstRegistPnttm;

    // 최종수정시점
    private String lastUpdtPnttm;

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
