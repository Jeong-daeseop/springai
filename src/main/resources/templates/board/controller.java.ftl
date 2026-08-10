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
<#if jakartaValidation>
import jakarta.validation.Valid;
<#else>
import javax.validation.Valid;
</#if>
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ${displayName} Controller
 * @author Claude AI
 * @since ${date}
 */
@Controller
@RequiredArgsConstructor
public class Egov${domain}Controller {

<#if route.defaultBbsId??>
    private static final String DEFAULT_BBS_ID = "${route.defaultBbsId}";
<#else>
    private static final String DEFAULT_BBS_ID = null;
</#if>

    private final ${domain}Service ${domainLc}Service;
    private final EgovPropertyService propertiesService;

    /** ${displayName} 목록 */
<#if route.hasListAlias()>
    @GetMapping({"${urlPrefix}List.do", "${route.registeredListPath}"})
<#else>
    @GetMapping("${urlPrefix}List.do")
</#if>
    public String select${domain}List(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        searchVO.setBbsId(resolveBbsId(searchVO.getBbsId()));
        if (isBlank(searchVO.getBbsId())) {
            populateLayoutModel(model, "board-list", "목록", null);
            model.addAttribute("message", "게시판 ID가 필요합니다.");
            return "${domainLc}/Egov${domain}List";
        }
<#if useTableName??>
        if (!isBlank(searchVO.getBbsId())) {
            String useAt = ${domainLc}Service.selectBoardUseAt(searchVO);
            if (!"Y".equals(useAt)) {
                populateLayoutModel(model, "board-list", "목록", searchVO.getBbsId());
                model.addAttribute("message", "사용하지 않는 게시판입니다.");
                return "${domainLc}/Egov${domain}List";
            }
        }
</#if>

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

        List<${domain}VO> resultList = ${domainLc}Service.select${domain}List(searchVO);
        model.addAttribute("resultList", resultList);
        model.addAttribute("paginationInfo", paginationInfo);
        populateLayoutModel(model, "board-list", "목록", searchVO.getBbsId());
        return "${domainLc}/Egov${domain}List";
    }

    /** ${displayName} 상세 (조회수 자동 증가) */
    @GetMapping("${urlPrefix}Detail.do")
    public String select${domain}(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {

        searchVO.setBbsId(resolveBbsId(searchVO.getBbsId()));
        if (!hasCompositeKey(searchVO)) {
            model.addAttribute("message", "게시판 ID와 게시물 번호가 필요합니다.");
            return redirectToList(searchVO.getBbsId());
        }
        ${domain}VO vo = ${domainLc}Service.select${domain}(searchVO);
        if (vo == null) {
            model.addAttribute("message", "게시물을 찾을 수 없습니다.");
            return redirectToList(searchVO.getBbsId());
        }
        ${domainLc}Service.update${domain}ReadCount(searchVO);
        model.addAttribute("result", vo);
        model.addAttribute("prevPost", ${domainLc}Service.selectPrev${domain}(searchVO));
        model.addAttribute("nextPost", ${domainLc}Service.selectNext${domain}(searchVO));
<#if hasFile && fileDetailTableName??>
        model.addAttribute("fileList", ${domainLc}Service.selectFileList(vo.get${atchFileId.javaName?cap_first}()));
</#if>
        populateLayoutModel(model, "board-list", "상세", searchVO.getBbsId());
        return "${domainLc}/Egov${domain}Detail";
    }

    /** ${displayName} 등록 화면 */
    @GetMapping("${urlPrefix}RegistView.do")
    public String insert${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {
        searchVO.setBbsId(resolveBbsId(searchVO.getBbsId()));
        if (isBlank(searchVO.getBbsId())) {
            model.addAttribute("message", "게시판 ID가 필요합니다.");
            return redirectToList(null);
        }
        ${domain}VO ${domainLc}VO = new ${domain}VO();
        ${domainLc}VO.setBbsId(searchVO.getBbsId());
        model.addAttribute("${domainLc}VO", ${domainLc}VO);
        populateLayoutModel(model, "board-regist", "등록", searchVO.getBbsId());
        return "${domainLc}/Egov${domain}Regist";
    }

    /** ${displayName} 등록 처리 */
    @PostMapping("${urlPrefix}Regist.do")
    public String insert${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model) throws Exception {
        ${domainLc}VO.setBbsId(resolveBbsId(${domainLc}VO.getBbsId()));
        if (isBlank(${domainLc}VO.getBbsId())) {
            bindingResult.reject("bbsId.required", "게시판 ID가 필요합니다.");
        }
        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "board-regist", "등록", ${domainLc}VO.getBbsId());
            return "${domainLc}/Egov${domain}Regist";
        }
<#list fields as f>
<#if f.javaName == "frstRegistPnttm">
        ${domainLc}VO.setFrstRegistPnttm(java.time.LocalDateTime.now().toString());
<#elseif f.javaName == "frstRegisterId">
        ${domainLc}VO.setFrstRegisterId("system");
<#elseif f.javaName == "useAt" && !formFields?seq_contains(f)>
        ${domainLc}VO.setUseAt("Y");
</#if>
</#list>
        ${domainLc}Service.insert${domain}(${domainLc}VO);
        return redirectToList(${domainLc}VO.getBbsId());
    }

    /** ${displayName} 수정 화면 */
    @GetMapping("${urlPrefix}UpdtView.do")
    public String update${domain}View(
            @ModelAttribute("searchVO") ${domain}VO searchVO,
            ModelMap model) throws Exception {
        searchVO.setBbsId(resolveBbsId(searchVO.getBbsId()));
        if (!hasCompositeKey(searchVO)) {
            model.addAttribute("message", "게시판 ID와 게시물 번호가 필요합니다.");
            return redirectToList(searchVO.getBbsId());
        }
        ${domain}VO result = ${domainLc}Service.select${domain}(searchVO);
        if (result == null) {
            model.addAttribute("message", "게시물을 찾을 수 없습니다.");
            return redirectToList(searchVO.getBbsId());
        }
        model.addAttribute("${domainLc}VO", result);
        populateLayoutModel(model, "board-regist", "수정", searchVO.getBbsId());
        return "${domainLc}/Egov${domain}Updt";
    }

    /** ${displayName} 수정 처리 */
    @PostMapping("${urlPrefix}Updt.do")
    public String update${domain}(
            @ModelAttribute("${domainLc}VO") @Valid ${domain}VO ${domainLc}VO,
            BindingResult bindingResult,
            ModelMap model) throws Exception {
        ${domainLc}VO.setBbsId(resolveBbsId(${domainLc}VO.getBbsId()));
        if (!hasCompositeKey(${domainLc}VO)) {
            bindingResult.reject("board.pk.required", "게시판 ID와 게시물 번호가 필요합니다.");
        }
        if (bindingResult.hasErrors()) {
            populateLayoutModel(model, "board-regist", "수정", ${domainLc}VO.getBbsId());
            return "${domainLc}/Egov${domain}Updt";
        }
        ${domainLc}Service.update${domain}(${domainLc}VO);
        return redirectToList(${domainLc}VO.getBbsId());
    }

    /** ${displayName} 논리삭제 */
    @PostMapping("${urlPrefix}Delete.do")
    public String delete${domain}(${domain}VO ${domainLc}VO, ModelMap model) throws Exception {
        ${domainLc}VO.setBbsId(resolveBbsId(${domainLc}VO.getBbsId()));
        if (!hasCompositeKey(${domainLc}VO)) {
            model.addAttribute("message", "게시판 ID와 게시물 번호가 필요합니다.");
            return redirectToList(${domainLc}VO.getBbsId());
        }
        ${domainLc}Service.delete${domain}(${domainLc}VO);
        return redirectToList(${domainLc}VO.getBbsId());
    }
    private void populateLayoutModel(ModelMap model, String currentMenuId, String currentPageSuffix, String bbsId) {
        String listUrl = withBbsId("${urlPrefix}List.do", bbsId);
        model.addAttribute("currentMenuId", currentMenuId);
        model.addAttribute("currentPageSuffix", currentPageSuffix);
        model.addAttribute("menuContextUrl", "${route.resolvedMenuContextUrl()}");
        model.addAttribute("resolvedBbsId", bbsId);
        model.addAttribute("lnbMenus", List.of(
                menu("board-list", "목록", listUrl),
                menu("board-regist", "글쓰기", withBbsId("${urlPrefix}RegistView.do", bbsId))
        ));
    }

    private String withBbsId(String baseUrl, String bbsId) {
        return isBlank(bbsId) ? baseUrl : baseUrl + "?bbsId=" + bbsId;
    }

    private String resolveBbsId(String requestedBbsId) {
        return isBlank(requestedBbsId) ? DEFAULT_BBS_ID : requestedBbsId;
    }

    private boolean hasCompositeKey(${domain}VO vo) {
        return vo != null && !isBlank(vo.getBbsId()) && vo.getNttId() != null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String redirectToList(String bbsId) {
        return "redirect:" + withBbsId("${urlPrefix}List.do", bbsId);
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
