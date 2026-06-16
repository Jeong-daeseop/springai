package ${packageName}.web;

import ${packageName}.service.${domain}Service;
import ${packageName}.service.${domain}VO;
import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @RequestMapping("${urlPrefix}List.do")
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

        return "${domainLc}/Egov${domain}List";
    }

    /**
     * ${domainKr} 상세 조회
     */
    @RequestMapping("${urlPrefix}Detail.do")
    public String select${domain}(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        ${domain}VO vo = ${domainLc}Service.select${domain}(searchVO);
        model.addAttribute("result", vo);
        return "${domainLc}/Egov${domain}Detail";
    }

    /**
     * ${domainKr} 등록 화면
     */
    @RequestMapping("${urlPrefix}RegistView.do")
    public String insert${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {
        model.addAttribute("${domainLc}VO", new ${domain}VO());
        return "${domainLc}/Egov${domain}Regist";
    }

    /**
     * ${domainKr} 등록
     */
    @RequestMapping("${urlPrefix}Regist.do")
    public String insert${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model) throws Exception {

        if (bindingResult.hasErrors()) {
            return "${domainLc}/Egov${domain}Regist";
        }
        ${domainLc}Service.insert${domain}(${domainLc}VO);
        return "forward:${urlPrefix}List.do";
    }

    /**
     * ${domainKr} 수정 화면
     */
    @RequestMapping("${urlPrefix}UpdtView.do")
    public String update${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        ${domain}VO vo = ${domainLc}Service.select${domain}(searchVO);
        model.addAttribute("${domainLc}VO", vo);
        return "${domainLc}/Egov${domain}Updt";
    }

    /**
     * ${domainKr} 수정
     */
    @RequestMapping("${urlPrefix}Updt.do")
    public String update${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model) throws Exception {

        if (bindingResult.hasErrors()) {
            return "${domainLc}/Egov${domain}Updt";
        }
        ${domainLc}Service.update${domain}(${domainLc}VO);
        return "forward:${urlPrefix}List.do";
    }

    /**
     * ${domainKr} 삭제
     */
    @RequestMapping("${urlPrefix}Delete.do")
    public String delete${domain}(
            ${domain}VO ${domainLc}VO,
            ModelMap model) throws Exception {

        ${domainLc}Service.delete${domain}(${domainLc}VO);
        return "forward:${urlPrefix}List.do";
    }
}
