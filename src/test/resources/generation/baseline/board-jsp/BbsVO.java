package egovframework.let.bbs.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * BBS VO
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Getter
@Setter
public class BbsVO extends BbsSearchVO {

    private static final long serialVersionUID = 1L;

    /** 게시판ID */
    @NotBlank
    @Size(max = 20)
    private String bbsId;

    /** 게시글번호 */
    private Long nttId;

    /** 제목 */
    @NotBlank
    @Size(max = 200)
    private String nttSj;

    /** 내용 */
    @Size(max = 4000)
    private String nttCn;

    /** 작성자명 */
    @Size(max = 50)
    private String ntcrNm;

    /** 공지여부 */
    @Size(max = 1)
    private String noticeAt;

    /** 첨부파일ID */
    @Size(max = 20)
    private String atchFileId;

    /** 최초등록시점 */
    private String frstRegistPnttm;

    /** 조회수 */
    private Integer rdcnt;

    /** 게시판명 (LETTNBBSMASTER.BBS_NM 조인 표시용) */
    private String bbsNm;

}
