package ${packageName}.web;

import ${packageName}.service.${domain}Service;
import ${packageName}.service.${domain}VO;
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
<#if jakartaValidation>
import jakarta.validation.Valid;
<#else>
import javax.validation.Valid;
</#if>
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ${domainKr} Controller
 * @author Claude AI
 * @since ${date}
 */
@Controller
@RequiredArgsConstructor
public class Egov${domain}Controller {

    private final ${domain}Service ${domainLc}Service;
    private final EgovPropertyService propertiesService;

    /**
     * ${domainKr} 목록 조회
     */
    @GetMapping("${urlPrefix}List.do")
    public String select${domain}List(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
        searchVO.setPageSize(propertiesService.getInt("pageSize"));

        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(searchVO.getPageSize());
        searchVO.setPaginationInfo(paginationInfo);

        int totCnt = ${domainLc}Service.select${domain}ListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);

        List<${domain}VO> ${domainLc}List = ${domainLc}Service.select${domain}List(searchVO);
        model.addAttribute("resultList", ${domainLc}List);
        model.addAttribute("paginationInfo", paginationInfo);
        populateLayoutModel(model, "crud-list", "${domainKr} 목록");

        return "${domainLc}/Egov${domain}List";
    }

    /**
     * ${domainKr} 상세 조회
     */
    @GetMapping("${urlPrefix}Detail.do")
    public String select${domain}(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        ${domain}VO vo = ${domainLc}Service.select${domain}(searchVO);
        model.addAttribute("result", vo);
        populateLayoutModel(model, "crud-list", "상세");
        return "${domainLc}/Egov${domain}Detail";
    }

    /**
     * ${domainKr} 등록 화면
     */
    @GetMapping("${urlPrefix}RegistView.do")
    public String insert${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {
        model.addAttribute("${domainLc}VO", new ${domain}VO());
        populateLayoutModel(model, "crud-regist", "등록");
        return "${domainLc}/Egov${domain}Regist";
    }

    /**
     * ${domainKr} 등록
     */
    @PostMapping("${urlPrefix}Regist.do")
    public String insert${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "crud-regist", "등록");
            return "${domainLc}/Egov${domain}Regist";
        }
        ${domainLc}Service.insert${domain}(${domainLc}VO);
        redirectAttributes.addFlashAttribute("message", "${domainKr}이(가) 등록되었습니다.");
        return "redirect:${urlPrefix}List.do";
    }

    /**
     * ${domainKr} 수정 화면
     */
    @GetMapping("${urlPrefix}UpdtView.do")
    public String update${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        ${domain}VO vo = ${domainLc}Service.select${domain}(searchVO);
        model.addAttribute("${domainLc}VO", vo);
        populateLayoutModel(model, "crud-regist", "수정");
        return "${domainLc}/Egov${domain}Updt";
    }

    /**
     * ${domainKr} 수정
     */
    @PostMapping("${urlPrefix}Updt.do")
    public String update${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "crud-regist", "수정");
            return "${domainLc}/Egov${domain}Updt";
        }
        ${domainLc}Service.update${domain}(${domainLc}VO);
        redirectAttributes.addFlashAttribute("message", "${domainKr}이(가) 수정되었습니다.");
        return "redirect:${urlPrefix}Detail.do?${pk.javaName}=" + ${domainLc}VO.get${pk.javaName?cap_first}();
    }

    /**
     * ${domainKr} 삭제
     */
    @PostMapping("${urlPrefix}Delete.do")
    public String delete${domain}(
            ${domain}VO ${domainLc}VO,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        ${domainLc}Service.delete${domain}(${domainLc}VO);
        redirectAttributes.addFlashAttribute("message", "${domainKr}이(가) 삭제되었습니다.");
        return "redirect:${urlPrefix}List.do";
    }

    private void populateLayoutModel(ModelMap model, String currentMenuId, String currentPageLabel) {
        String listUrl = "${urlPrefix}List.do";
        model.addAttribute("currentMenuId", currentMenuId);
        model.addAttribute("lnbTitle", "${domainKr} 관리");
        model.addAttribute("lnbMenus", List.of(
                menu("crud-list", "목록", listUrl),
                menu("crud-regist", "등록", "${urlPrefix}RegistView.do")
        ));

        List<Map<String, String>> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(crumb("홈", "/"));
        breadcrumbs.add(crumb("업무관리", "/"));
        if (!"${domainKr} 목록".equals(currentPageLabel)) {
            breadcrumbs.add(crumb("${domainKr} 목록", listUrl));
        }
        breadcrumbs.add(crumb(currentPageLabel, null));
        model.addAttribute("breadcrumbs", breadcrumbs);
    }

    private Map<String, String> menu(String menuId, String label, String url) {
        return Map.of("menuId", menuId, "label", label, "url", url);
    }

    private Map<String, String> crumb(String label, String url) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("url", url);
        return item;
    }
}
