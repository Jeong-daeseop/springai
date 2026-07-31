package egovframework.let.bbs.web;

import egovframework.let.bbs.service.BbsuseVO;
import egovframework.let.bbs.service.BbsMasterService;
import egovframework.let.bbs.service.BbsMasterVO;
import lombok.RequiredArgsConstructor;
import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BBSMASTER Controller
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Controller
@RequiredArgsConstructor
public class EgovBbsMasterController {

    private final BbsMasterService bbsMasterService;
    private final EgovPropertyService propertiesService;

    @GetMapping("/bbs/bbsMasterList.do")
    public String selectBbsMasterList(
            @ModelAttribute("searchVO") BbsMasterVO searchVO,
            ModelMap model) throws Exception {

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

        int totCnt = bbsMasterService.selectBbsMasterListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);

        List<BbsMasterVO> bbsMasterList =
                bbsMasterService.selectBbsMasterList(searchVO);
        model.addAttribute("resultList", bbsMasterList);
        model.addAttribute("paginationInfo", paginationInfo);
        populateLayoutModel(model, "masterdetail-list", "BBSMASTER 목록");

        return "bbsMaster/EgovBbsMasterList";
    }

    @GetMapping("/bbs/bbsMasterDetail.do")
    public String selectBbsMaster(
            @ModelAttribute("searchVO") BbsMasterVO searchVO,
            ModelMap model) throws Exception {

        BbsMasterVO vo = bbsMasterService.selectBbsMaster(searchVO);
        List<BbsuseVO> detailList =
                bbsMasterService.selectBbsuseList(searchVO.getBbsId());
        model.addAttribute("result", vo);
        model.addAttribute("detailList", detailList);
        populateLayoutModel(model, "masterdetail-list", "상세");
        return "bbsMaster/EgovBbsMasterDetail";
    }

    @GetMapping("/bbs/bbsMasterRegistView.do")
    public String insertBbsMasterView(
            @ModelAttribute("searchVO") BbsMasterVO searchVO,
            ModelMap model) throws Exception {
        model.addAttribute("bbsMasterVO", new BbsMasterVO());
        populateLayoutModel(model, "masterdetail-regist", "등록");
        return "bbsMaster/EgovBbsMasterRegist";
    }

    @PostMapping("/bbs/bbsMasterRegist.do")
    public String insertBbsMaster(
            @ModelAttribute("bbsMasterVO") @Valid BbsMasterVO bbsMasterVO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "masterdetail-regist", "등록");
            return "bbsMaster/EgovBbsMasterRegist";
        }
        bbsMasterService.insertBbsMaster(bbsMasterVO);
        redirectAttributes.addFlashAttribute("message", "BBSMASTER이(가) 등록되었습니다.");
        return "redirect:/bbs/bbsMasterList.do";
    }

    @GetMapping("/bbs/bbsMasterUpdtView.do")
    public String updateBbsMasterView(
            @ModelAttribute("searchVO") BbsMasterVO searchVO,
            ModelMap model) throws Exception {

        BbsMasterVO vo = bbsMasterService.selectBbsMaster(searchVO);
        model.addAttribute("bbsMasterVO", vo);
        populateLayoutModel(model, "masterdetail-list", "수정");
        return "bbsMaster/EgovBbsMasterUpdt";
    }

    @PostMapping("/bbs/bbsMasterUpdt.do")
    public String updateBbsMaster(
            @ModelAttribute("bbsMasterVO") @Valid BbsMasterVO bbsMasterVO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "masterdetail-list", "수정");
            return "bbsMaster/EgovBbsMasterUpdt";
        }
        bbsMasterService.updateBbsMaster(bbsMasterVO);
        redirectAttributes.addFlashAttribute("message", "BBSMASTER이(가) 수정되었습니다.");
        return "redirect:/bbs/bbsMasterDetail.do?bbsId=" + bbsMasterVO.getBbsId();
    }

    @PostMapping("/bbs/bbsMasterDelete.do")
    public String deleteBbsMaster(
            BbsMasterVO bbsMasterVO,
            RedirectAttributes redirectAttributes) throws Exception {

        bbsMasterService.deleteBbsMaster(bbsMasterVO);
        redirectAttributes.addFlashAttribute("message", "BBSMASTER이(가) 삭제되었습니다.");
        return "redirect:/bbs/bbsMasterList.do";
    }

    @PostMapping("/bbs/bbsMasterBulkDelete.do")
    public String deleteBbsMasterBulk(
            @RequestParam("ids") List<String> ids,
            RedirectAttributes redirectAttributes) throws Exception {

        int deleted = bbsMasterService.deleteBbsMasterBulk(ids);
        redirectAttributes.addFlashAttribute("message", deleted + "건이 삭제되었습니다.");
        return "redirect:/bbs/bbsMasterList.do";
    }

    private void populateLayoutModel(ModelMap model, String currentMenuId, String currentPageLabel) {
        String listUrl = "/bbs/bbsMasterList.do";
        model.addAttribute("currentMenuId", currentMenuId);
        model.addAttribute("lnbTitle", "BBSMASTER 관리");
        model.addAttribute("lnbMenus", List.of(
                menu("masterdetail-list", "목록", listUrl),
                menu("masterdetail-regist", "등록", "/bbs/bbsMasterRegistView.do")
        ));

        List<Map<String, String>> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(crumb("홈", "/"));
        breadcrumbs.add(crumb("업무관리", "/"));
        if (!"BBSMASTER 목록".equals(currentPageLabel)) {
            breadcrumbs.add(crumb("BBSMASTER 목록", listUrl));
        }
        breadcrumbs.add(crumb(currentPageLabel, null));
        model.addAttribute("breadcrumbs", breadcrumbs);
    }

    private Map<String, Object> menu(String menuId, String label, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("menuId", menuId);
        item.put("label", label);
        item.put("url", url);
        item.put("children", List.of());
        return item;
    }

    private Map<String, String> crumb(String label, String url) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("url", url);
        return item;
    }
}
