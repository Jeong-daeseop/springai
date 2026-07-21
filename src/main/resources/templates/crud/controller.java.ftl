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
import java.util.List;

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
<#if route.hasListAlias()>
    @GetMapping({"${route.canonicalListPath()}", "${route.registeredListPath()}"})
<#else>
    @GetMapping("${route.canonicalListPath()}")
</#if>
    public String select${domain}List(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
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

        int totCnt = ${domainLc}Service.select${domain}ListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);

        List<${domain}VO> ${domainLc}List = ${domainLc}Service.select${domain}List(searchVO);
        model.addAttribute("resultList", ${domainLc}List);
        model.addAttribute("paginationInfo", paginationInfo);
        populateLayoutModel(model, "crud-list", "목록");

        return "${domainLc}/Egov${domain}List";
    }

    /**
     * ${domainKr} 상세 조회
     */
<#if route.hasDetailAlias()>
    @GetMapping({"${route.canonicalDetailPath()}", "${route.registeredDetailPath()}"})
<#else>
    @GetMapping("${route.canonicalDetailPath()}")
</#if>
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
<#if route.hasRegistViewAlias()>
    @GetMapping({"${route.canonicalRegistViewPath()}", "${route.registeredRegistViewPath()}"})
<#else>
    @GetMapping("${route.canonicalRegistViewPath()}")
</#if>
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
<#if route.hasRegistAlias()>
    @PostMapping({"${route.canonicalRegistPath()}", "${route.registeredRegistPath()}"})
<#else>
    @PostMapping("${route.canonicalRegistPath()}")
</#if>
    public String insert${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "crud-regist", "등록");
            return "${domainLc}/Egov${domain}Regist";
        }
<#list fields as f><#if f.javaName == "frstRegistPnttm">
        ${domainLc}VO.setFrstRegistPnttm(java.time.LocalDateTime.now().toString());
</#if><#if f.javaName == "frstRegisterId">
        ${domainLc}VO.setFrstRegisterId("system");
</#if></#list>
        ${domainLc}Service.insert${domain}(${domainLc}VO);
        redirectAttributes.addFlashAttribute("message", "${domainKr}이(가) 등록되었습니다.");
        return "redirect:${route.canonicalListPath()}";
    }

    /**
     * ${domainKr} 수정 화면
     */
<#if route.hasUpdtViewAlias()>
    @GetMapping({"${route.canonicalUpdtViewPath()}", "${route.registeredUpdtViewPath()}"})
<#else>
    @GetMapping("${route.canonicalUpdtViewPath()}")
</#if>
    public String update${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        ${domain}VO vo = ${domainLc}Service.select${domain}(searchVO);
        model.addAttribute("${domainLc}VO", vo);
        populateLayoutModel(model, "crud-list", "수정");
        return "${domainLc}/Egov${domain}Updt";
    }

    /**
     * ${domainKr} 수정
     */
<#if route.hasUpdtAlias()>
    @PostMapping({"${route.canonicalUpdtPath()}", "${route.registeredUpdtPath()}"})
<#else>
    @PostMapping("${route.canonicalUpdtPath()}")
</#if>
    public String update${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "crud-list", "수정");
            return "${domainLc}/Egov${domain}Updt";
        }
<#list fields as f><#if f.javaName == "lastUpdtPnttm">
        ${domainLc}VO.setLastUpdtPnttm(java.time.LocalDateTime.now().toString());
</#if><#if f.javaName == "lastUpdusrId">
        ${domainLc}VO.setLastUpdusrId("system");
</#if></#list>
        ${domainLc}Service.update${domain}(${domainLc}VO);
        redirectAttributes.addFlashAttribute("message", "${domainKr}이(가) 수정되었습니다.");
        StringBuilder redirectUrl = new StringBuilder("redirect:${route.canonicalDetailPath()}?");
<#list pkFields as p>
        redirectUrl.append("${p.javaName}=").append(${domainLc}VO.get${p.javaName?cap_first}())<#if p?has_next>.append("&")</#if>;
</#list>
        return redirectUrl.toString();
    }

    /**
     * ${domainKr} 삭제
     */
<#if route.hasDeleteAlias()>
    @PostMapping({"${route.canonicalDeletePath()}", "${route.registeredDeletePath()}"})
<#else>
    @PostMapping("${route.canonicalDeletePath()}")
</#if>
    public String delete${domain}(
            ${domain}VO ${domainLc}VO,
            ModelMap model,
            RedirectAttributes redirectAttributes) throws Exception {

        ${domainLc}Service.delete${domain}(${domainLc}VO);
        redirectAttributes.addFlashAttribute("message", "${domainKr}이(가) 삭제되었습니다.");
        return "redirect:${route.canonicalListPath()}";
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
        model.addAttribute("menuContextUrl", "${route.resolvedMenuContextUrl()}");
    }
}
