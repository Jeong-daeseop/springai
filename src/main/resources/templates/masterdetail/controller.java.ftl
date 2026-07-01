package ${packageName}.web;

import ${packageName}.service.${detail.domain}VO;
import ${packageName}.service.${master.domain}Service;
import ${packageName}.service.${master.domain}VO;
import lombok.RequiredArgsConstructor;
import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ${master.domainKr} Controller
 * @author Claude AI
 * @since ${date}
 */
@Controller
@RequiredArgsConstructor
public class Egov${master.domain}Controller {

    private final ${master.domain}Service ${master.domainLc}Service;
    private final EgovPropertyService propertiesService;

    @GetMapping("${urlPrefix}List.do")
    public String select${master.domain}List(
            @ModelAttribute("searchVO") ${master.domain}VO searchVO,
            ModelMap model) throws Exception {

        searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
        searchVO.setPageSize(propertiesService.getInt("pageSize"));

        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(searchVO.getPageSize());
        searchVO.setPaginationInfo(paginationInfo);

        int totCnt = ${master.domainLc}Service.select${master.domain}ListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);

        List<${master.domain}VO> ${master.domainLc}List =
                ${master.domainLc}Service.select${master.domain}List(searchVO);
        model.addAttribute("resultList", ${master.domainLc}List);
        model.addAttribute("paginationInfo", paginationInfo);
        populateLayoutModel(model, "masterdetail-list", "${master.domainKr} 목록");

        return "${master.domainLc}/Egov${master.domain}List";
    }

    @GetMapping("${urlPrefix}Detail.do")
    public String select${master.domain}(
            @ModelAttribute("searchVO") ${master.domain}VO searchVO,
            ModelMap model) throws Exception {

        ${master.domain}VO vo = ${master.domainLc}Service.select${master.domain}(searchVO);
        List<${detail.domain}VO> detailList =
                ${master.domainLc}Service.select${detail.domain}List(searchVO.get${master.pk.javaName?cap_first}());
        model.addAttribute("result", vo);
        model.addAttribute("detailList", detailList);
        populateLayoutModel(model, "masterdetail-list", "상세");
        return "${master.domainLc}/Egov${master.domain}Detail";
    }

    @GetMapping("${urlPrefix}RegistView.do")
    public String insert${master.domain}View(
            @ModelAttribute("searchVO") ${master.domain}VO searchVO,
            ModelMap model) throws Exception {
        model.addAttribute("${master.domainLc}VO", new ${master.domain}VO());
        populateLayoutModel(model, "masterdetail-regist", "등록");
        return "${master.domainLc}/Egov${master.domain}Regist";
    }

    @PostMapping("${urlPrefix}Regist.do")
    public String insert${master.domain}(
            @ModelAttribute("${master.domainLc}VO") @Valid ${master.domain}VO ${master.domainLc}VO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "masterdetail-regist", "등록");
            return "${master.domainLc}/Egov${master.domain}Regist";
        }
        ${master.domainLc}Service.insert${master.domain}(${master.domainLc}VO);
        redirectAttributes.addFlashAttribute("message", "${master.domainKr}이(가) 등록되었습니다.");
        return "redirect:${urlPrefix}List.do";
    }

    @PostMapping("${urlPrefix}Delete.do")
    public String delete${master.domain}(
            ${master.domain}VO ${master.domainLc}VO,
            RedirectAttributes redirectAttributes) throws Exception {

        ${master.domainLc}Service.delete${master.domain}(${master.domainLc}VO);
        redirectAttributes.addFlashAttribute("message", "${master.domainKr}이(가) 삭제되었습니다.");
        return "redirect:${urlPrefix}List.do";
    }

    private void populateLayoutModel(ModelMap model, String currentMenuId, String currentPageLabel) {
        String listUrl = "${urlPrefix}List.do";
        model.addAttribute("currentMenuId", currentMenuId);
        model.addAttribute("lnbTitle", "${master.domainKr} 관리");
        model.addAttribute("lnbMenus", List.of(
                menu("masterdetail-list", "목록", listUrl),
                menu("masterdetail-regist", "등록", "${urlPrefix}RegistView.do")
        ));

        List<Map<String, String>> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(crumb("홈", "/"));
        breadcrumbs.add(crumb("업무관리", "/"));
        if (!"${master.domainKr} 목록".equals(currentPageLabel)) {
            breadcrumbs.add(crumb("${master.domainKr} 목록", listUrl));
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
