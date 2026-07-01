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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** ${domainKr} 목록 */
    @RequestMapping("${urlPrefix}List.do")
    public String select${domain}List(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        if (searchVO.getBbsId() != null && !searchVO.getBbsId().isEmpty()) {
            String useAt = ${domainLc}Service.selectBoardUseAt(searchVO);
            if (!"Y".equals(useAt)) {
                populateLayoutModel(model, "board-list", "${domainKr} 목록", searchVO.getBbsId());
                model.addAttribute("message", "사용하지 않는 게시판입니다.");
                return "${domainLc}/Egov${domain}List";
            }
        }

        searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
        searchVO.setPageSize(propertiesService.getInt("pageSize"));

        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(searchVO.getPageSize());
        searchVO.setPaginationInfo(paginationInfo);

        int totCnt = ${domainLc}Service.select${domain}ListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);

        List<${domain}VO> resultList = ${domainLc}Service.select${domain}List(searchVO);
        model.addAttribute("resultList", resultList);
        model.addAttribute("paginationInfo", paginationInfo);
        populateLayoutModel(model, "board-list", "${domainKr} 목록", searchVO.getBbsId());
        return "${domainLc}/Egov${domain}List";
    }

    /** ${domainKr} 상세 (조회수 자동 증가) */
    @RequestMapping("${urlPrefix}Detail.do")
    public String select${domain}(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        ${domain}VO vo = ${domainLc}Service.select${domain}(searchVO);
        model.addAttribute("result", vo);
        model.addAttribute("prevPost", ${domainLc}Service.selectPrev${domain}(searchVO));
        model.addAttribute("nextPost", ${domainLc}Service.selectNext${domain}(searchVO));
        populateLayoutModel(model, "board-list", "${domainKr} 상세", searchVO.getBbsId());
        return "${domainLc}/Egov${domain}Detail";
    }

    /** ${domainKr} 등록 화면 */
    @RequestMapping("${urlPrefix}RegistView.do")
    public String insert${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {
        ${domain}VO ${domainLc}VO = new ${domain}VO();
        ${domainLc}VO.setBbsId(searchVO.getBbsId());
        model.addAttribute("${domainLc}VO", ${domainLc}VO);
        populateLayoutModel(model, "board-regist", "등록", searchVO.getBbsId());
        return "${domainLc}/Egov${domain}Regist";
    }

    /** ${domainKr} 등록 처리 */
    @RequestMapping("${urlPrefix}Regist.do")
    public String insert${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model) throws Exception {
        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "board-regist", "등록", ${domainLc}VO.getBbsId());
            return "${domainLc}/Egov${domain}Regist";
        }
        ${domainLc}Service.insert${domain}(${domainLc}VO);
        return "forward:${urlPrefix}List.do";
    }

    /** ${domainKr} 수정 화면 */
    @RequestMapping("${urlPrefix}UpdtView.do")
    public String update${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {
        model.addAttribute("${domainLc}VO", ${domainLc}Service.select${domain}(searchVO));
        populateLayoutModel(model, "board-regist", "수정", searchVO.getBbsId());
        return "${domainLc}/Egov${domain}Updt";
    }

    /** ${domainKr} 수정 처리 */
    @RequestMapping("${urlPrefix}Updt.do")
    public String update${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model) throws Exception {
        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "board-regist", "수정", ${domainLc}VO.getBbsId());
            return "${domainLc}/Egov${domain}Updt";
        }
        ${domainLc}Service.update${domain}(${domainLc}VO);
        return "forward:${urlPrefix}List.do";
    }

    /** ${domainKr} 논리삭제 */
    @RequestMapping("${urlPrefix}Delete.do")
    public String delete${domain}(${domain}VO ${domainLc}VO) throws Exception {
        ${domainLc}Service.delete${domain}(${domainLc}VO);
        return "forward:${urlPrefix}List.do";
    }
<#if hasFile>

    /** 첨부파일 다운로드 URL 반환 */
    @RequestMapping("${urlPrefix}FileDownload.do")
    public String fileDownload(${domain}VO ${domainLc}VO, ModelMap model) throws Exception {
        // TODO: 파일 스트림 처리는 환경별로 구현
        model.addAttribute("atchFileId", ${domainLc}VO.getAtchFileId());
        return "forward:${urlPrefix}Detail.do";
    }
</#if>

    private void populateLayoutModel(ModelMap model, String currentMenuId, String currentPageLabel, String bbsId) {
        String listUrl = withBbsId("${urlPrefix}List.do", bbsId);
        model.addAttribute("currentMenuId", currentMenuId);
        model.addAttribute("lnbTitle", "${domainKr}");
        model.addAttribute("lnbMenus", List.of(
                menu("board-list", "목록", listUrl),
                menu("board-regist", "글쓰기", withBbsId("${urlPrefix}RegistView.do", bbsId))
        ));

        List<Map<String, String>> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(crumb("홈", "/"));
        breadcrumbs.add(crumb("소식·뉴스", "/"));
        if (!"${domainKr} 목록".equals(currentPageLabel)) {
            breadcrumbs.add(crumb("${domainKr} 목록", listUrl));
        }
        breadcrumbs.add(crumb(currentPageLabel, null));
        model.addAttribute("breadcrumbs", breadcrumbs);
    }

    private String withBbsId(String baseUrl, String bbsId) {
        return (bbsId == null || bbsId.isEmpty()) ? baseUrl : baseUrl + "?bbsId=" + bbsId;
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
