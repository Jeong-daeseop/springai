package egovframework.let.bbs.web;

import egovframework.let.bbs.service.BbsService;
import egovframework.let.bbs.service.BbsVO;
import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BBS Controller
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Controller
@RequiredArgsConstructor
public class EgovBbsController {

    private static final String DEFAULT_BBS_ID = null;

    private final BbsService bbsService;
    private final EgovPropertyService propertiesService;

    /** BBS 목록 */
    @RequestMapping("/bbs/bbsList.do")
    public String selectBbsList(
            @ModelAttribute("searchVO") BbsVO searchVO,
            ModelMap model) throws Exception {

        searchVO.setBbsId(resolveBbsId(searchVO.getBbsId()));
        if (isBlank(searchVO.getBbsId())) {
            populateLayoutModel(model, "board-list", "목록", null);
            model.addAttribute("message", "게시판 ID가 필요합니다.");
            return "bbs/EgovBbsList";
        }
        if (!isBlank(searchVO.getBbsId())) {
            String useAt = bbsService.selectBoardUseAt(searchVO);
            if (!"Y".equals(useAt)) {
                populateLayoutModel(model, "board-list", "목록", searchVO.getBbsId());
                model.addAttribute("message", "사용하지 않는 게시판입니다.");
                return "bbs/EgovBbsList";
            }
        }

        int requestedPageUnit = searchVO.getPageUnit();
        if (requestedPageUnit != 10 && requestedPageUnit != 20 && requestedPageUnit != 50) {
            requestedPageUnit = propertiesService.getInt("pageUnit");
        }
        searchVO.setPageUnit(requestedPageUnit);
        searchVO.setPageSize(propertiesService.getInt("pageSize"));

        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(searchVO.getPageSize());
        searchVO.setPaginationInfo(paginationInfo);

        int totCnt = bbsService.selectBbsListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);

        List<BbsVO> resultList = bbsService.selectBbsList(searchVO);
        model.addAttribute("resultList", resultList);
        model.addAttribute("paginationInfo", paginationInfo);
        populateLayoutModel(model, "board-list", "목록", searchVO.getBbsId());
        return "bbs/EgovBbsList";
    }

    /** BBS 상세 (조회수 자동 증가) */
    @RequestMapping("/bbs/bbsDetail.do")
    public String selectBbs(
            @ModelAttribute("searchVO") BbsVO searchVO,
            ModelMap model) throws Exception {

        searchVO.setBbsId(resolveBbsId(searchVO.getBbsId()));
        if (!hasCompositeKey(searchVO)) {
            model.addAttribute("message", "게시판 ID와 게시물 번호가 필요합니다.");
            return redirectToList(searchVO.getBbsId());
        }
        BbsVO vo = bbsService.selectBbs(searchVO);
        if (vo == null) {
            model.addAttribute("message", "게시물을 찾을 수 없습니다.");
            return redirectToList(searchVO.getBbsId());
        }
        bbsService.updateBbsReadCount(searchVO);
        model.addAttribute("result", vo);
        model.addAttribute("prevPost", bbsService.selectPrevBbs(searchVO));
        model.addAttribute("nextPost", bbsService.selectNextBbs(searchVO));
        model.addAttribute("fileList", bbsService.selectFileList(vo.getAtchFileId()));
        populateLayoutModel(model, "board-list", "상세", searchVO.getBbsId());
        return "bbs/EgovBbsDetail";
    }

    /** BBS 등록 화면 */
    @RequestMapping("/bbs/bbsRegistView.do")
    public String insertBbsView(
            @ModelAttribute("searchVO") BbsVO searchVO,
            ModelMap model) throws Exception {
        searchVO.setBbsId(resolveBbsId(searchVO.getBbsId()));
        if (isBlank(searchVO.getBbsId())) {
            model.addAttribute("message", "게시판 ID가 필요합니다.");
            return redirectToList(null);
        }
        BbsVO bbsVO = new BbsVO();
        bbsVO.setBbsId(searchVO.getBbsId());
        model.addAttribute("bbsVO", bbsVO);
        populateLayoutModel(model, "board-regist", "등록", searchVO.getBbsId());
        return "bbs/EgovBbsRegist";
    }

    /** BBS 등록 처리 */
    @RequestMapping("/bbs/bbsRegist.do")
    public String insertBbs(
            @ModelAttribute("bbsVO") @Valid BbsVO bbsVO,
            BindingResult bindingResult,
            ModelMap model) throws Exception {
        bbsVO.setBbsId(resolveBbsId(bbsVO.getBbsId()));
        if (isBlank(bbsVO.getBbsId())) {
            bindingResult.reject("bbsId.required", "게시판 ID가 필요합니다.");
        }
        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "board-regist", "등록", bbsVO.getBbsId());
            return "bbs/EgovBbsRegist";
        }
        bbsService.insertBbs(bbsVO);
        return redirectToList(bbsVO.getBbsId());
    }

    /** BBS 수정 화면 */
    @RequestMapping("/bbs/bbsUpdtView.do")
    public String updateBbsView(
            @ModelAttribute("searchVO") BbsVO searchVO,
            ModelMap model) throws Exception {
        searchVO.setBbsId(resolveBbsId(searchVO.getBbsId()));
        if (!hasCompositeKey(searchVO)) {
            model.addAttribute("message", "게시판 ID와 게시물 번호가 필요합니다.");
            return redirectToList(searchVO.getBbsId());
        }
        BbsVO result = bbsService.selectBbs(searchVO);
        if (result == null) {
            model.addAttribute("message", "게시물을 찾을 수 없습니다.");
            return redirectToList(searchVO.getBbsId());
        }
        model.addAttribute("bbsVO", result);
        populateLayoutModel(model, "board-regist", "수정", searchVO.getBbsId());
        return "bbs/EgovBbsUpdt";
    }

    /** BBS 수정 처리 */
    @RequestMapping("/bbs/bbsUpdt.do")
    public String updateBbs(
            @ModelAttribute("bbsVO") @Valid BbsVO bbsVO,
            BindingResult bindingResult,
            ModelMap model) throws Exception {
        bbsVO.setBbsId(resolveBbsId(bbsVO.getBbsId()));
        if (!hasCompositeKey(bbsVO)) {
            bindingResult.reject("board.pk.required", "게시판 ID와 게시물 번호가 필요합니다.");
        }
        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "board-regist", "수정", bbsVO.getBbsId());
            return "bbs/EgovBbsUpdt";
        }
        bbsService.updateBbs(bbsVO);
        return redirectToList(bbsVO.getBbsId());
    }

    /** BBS 논리삭제 */
    @RequestMapping("/bbs/bbsDelete.do")
    public String deleteBbs(BbsVO bbsVO, ModelMap model) throws Exception {
        bbsVO.setBbsId(resolveBbsId(bbsVO.getBbsId()));
        if (!hasCompositeKey(bbsVO)) {
            model.addAttribute("message", "게시판 ID와 게시물 번호가 필요합니다.");
            return redirectToList(bbsVO.getBbsId());
        }
        bbsService.deleteBbs(bbsVO);
        return redirectToList(bbsVO.getBbsId());
    }
    private void populateLayoutModel(ModelMap model, String currentMenuId, String currentPageSuffix, String bbsId) {
        String listUrl = withBbsId("/bbs/bbsList.do", bbsId);
        model.addAttribute("currentMenuId", currentMenuId);
        model.addAttribute("currentPageSuffix", currentPageSuffix);
        model.addAttribute("menuContextUrl", "/bbs/bbsList.do");
        model.addAttribute("resolvedBbsId", bbsId);
        model.addAttribute("lnbMenus", List.of(
                menu("board-list", "목록", listUrl),
                menu("board-regist", "글쓰기", withBbsId("/bbs/bbsRegistView.do", bbsId))
        ));
    }

    private String withBbsId(String baseUrl, String bbsId) {
        return isBlank(bbsId) ? baseUrl : baseUrl + "?bbsId=" + bbsId;
    }

    private String resolveBbsId(String requestedBbsId) {
        return isBlank(requestedBbsId) ? DEFAULT_BBS_ID : requestedBbsId;
    }

    private boolean hasCompositeKey(BbsVO vo) {
        return vo != null && !isBlank(vo.getBbsId()) && vo.getNttId() != null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String redirectToList(String bbsId) {
        return "redirect:" + withBbsId("/bbs/bbsList.do", bbsId);
    }

    private Map<String, Object> menu(String menuId, String label, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("menuId", menuId);
        item.put("label", label);
        item.put("url", url);
        item.put("children", List.of());
        return item;
    }

}
