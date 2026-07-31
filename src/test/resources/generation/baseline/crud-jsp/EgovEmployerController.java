package egovframework.let.emp.web;

import egovframework.let.emp.service.EmployerService;
import egovframework.let.emp.service.EmployerVO;
import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;
import java.util.List;

/**
 * EMPLYRINFO Controller
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Controller
@RequiredArgsConstructor
public class EgovEmployerController {

    private final EmployerService employerService;
    private final EgovPropertyService propertiesService;

    /**
     * EMPLYRINFO 목록 조회
     */
    @GetMapping("/emp/employerList.do")
    public String selectEmployerList(
            @ModelAttribute("searchVO") EmployerVO searchVO,
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

        int totCnt = employerService.selectEmployerListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);

        List<EmployerVO> employerList = employerService.selectEmployerList(searchVO);
        model.addAttribute("resultList", employerList);
        model.addAttribute("paginationInfo", paginationInfo);
        populateLayoutModel(model, "crud-list", "목록");

        return "employer/EgovEmployerList";
    }

    /**
     * EMPLYRINFO 상세 조회
     */
    @GetMapping("/emp/employerDetail.do")
    public String selectEmployer(
            @ModelAttribute("searchVO") EmployerVO searchVO,
            ModelMap model) throws Exception {

        EmployerVO vo = employerService.selectEmployer(searchVO);
        model.addAttribute("result", vo);
        populateLayoutModel(model, "crud-list", "상세");
        return "employer/EgovEmployerDetail";
    }

    /**
     * EMPLYRINFO 등록 화면
     */
    @GetMapping("/emp/employerRegistView.do")
    public String insertEmployerView(
            @ModelAttribute("searchVO") EmployerVO searchVO,
            ModelMap model) throws Exception {
        model.addAttribute("employerVO", new EmployerVO());
        populateLayoutModel(model, "crud-regist", "등록");
        return "employer/EgovEmployerRegist";
    }

    /**
     * EMPLYRINFO 등록
     */
    @PostMapping("/emp/employerRegist.do")
    public String insertEmployer(
            @ModelAttribute("employerVO") @Valid EmployerVO employerVO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "crud-regist", "등록");
            return "employer/EgovEmployerRegist";
        }
        employerVO.setFrstRegistPnttm(java.time.LocalDateTime.now().toString());
        employerService.insertEmployer(employerVO);
        redirectAttributes.addFlashAttribute("message", "EMPLYRINFO이(가) 등록되었습니다.");
        return "redirect:/emp/employerList.do";
    }

    /**
     * EMPLYRINFO 수정 화면
     */
    @GetMapping("/emp/employerUpdtView.do")
    public String updateEmployerView(
            @ModelAttribute("searchVO") EmployerVO searchVO,
            ModelMap model) throws Exception {

        EmployerVO vo = employerService.selectEmployer(searchVO);
        model.addAttribute("employerVO", vo);
        populateLayoutModel(model, "crud-list", "수정");
        return "employer/EgovEmployerUpdt";
    }

    /**
     * EMPLYRINFO 수정
     */
    @PostMapping("/emp/employerUpdt.do")
    public String updateEmployer(
            @ModelAttribute("employerVO") @Valid EmployerVO employerVO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "crud-list", "수정");
            return "employer/EgovEmployerUpdt";
        }
        employerVO.setLastUpdtPnttm(java.time.LocalDateTime.now().toString());
        employerService.updateEmployer(employerVO);
        redirectAttributes.addFlashAttribute("message", "EMPLYRINFO이(가) 수정되었습니다.");
        StringBuilder redirectUrl = new StringBuilder("redirect:/emp/employerDetail.do?");
        redirectUrl.append("emplyrId=").append(employerVO.getEmplyrId());
        return redirectUrl.toString();
    }

    /**
     * EMPLYRINFO 삭제
     */
    @PostMapping("/emp/employerDelete.do")
    public String deleteEmployer(
            EmployerVO employerVO,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        employerService.deleteEmployer(employerVO);
        redirectAttributes.addFlashAttribute("message", "EMPLYRINFO이(가) 삭제되었습니다.");
        return "redirect:/emp/employerList.do";
    }

    /**
     * LNB/브레드크럼은 EgovGnbMenuInterceptor가 LETTNPROGRMLIST.URL과 현재 요청 경로를 매칭해 채운다.
     * 목록(list) 화면만 메뉴에 직접 등록되는 경우가 일반적이라, 상세/등록/수정 요청도 같은 메뉴
     * 문맥을 쓰도록 menuContextUrl(목록 화면의 최종 URL)을 함께 넘긴다 — 인터셉터가 이 값을
     * 메뉴 URL과 비교해 현재 메뉴를 찾는다. currentPageSuffix는 "게시판 상세"처럼 브레드크럼
     * 마지막 항목에 화면 종류를 붙이는 데 쓰인다.
     */
    private void populateLayoutModel(ModelMap model, String currentMenuId, String currentPageSuffix) {
        model.addAttribute("currentMenuId", currentMenuId);
        model.addAttribute("currentPageSuffix", currentPageSuffix);
        model.addAttribute("menuContextUrl", "/emp/employerList.do");
    }
}
