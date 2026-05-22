package com.krdevops.springai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * eGovFrame 5.x 표준 소스 템플릿 제공 Tool.
 * Claude는 이 템플릿의 플레이스홀더를 실제 값으로 치환하여 소스를 생성합니다.
 *
 * 플레이스홀더 규칙:
 *   {{PACKAGE}}          - 패키지명          예) egovframework.let.emp
 *   {{DOMAIN}}           - 도메인명(대문자)   예) Employer
 *   {{DOMAIN_LC}}        - 도메인명(소문자)   예) employer
 *   {{DOMAIN_KR}}        - 도메인명(한국어)   예) 직원
 *   {{TABLE_NAME}}       - 테이블명          예) COMTNEMPLYRINFO
 *   {{VO_FIELDS}}        - VO 필드 목록      예) private String emplyrId;
 *   {{PK_FIELD}}         - PK 필드명         예) emplyrId
 *   {{PK_COLUMN}}        - PK 컬럼명         예) EMPLYR_ID
 *   {{PK_TYPE}}          - PK Java 타입      예) String
 *   {{MAPPER_COLUMNS}}   - SELECT 컬럼 목록  예) EMPLYR_ID, USER_NM, ...
 *   {{INSERT_COLUMNS}}   - INSERT 컬럼 목록
 *   {{JSP_LIST_COLUMNS}} - 목록 th/td 반복   예) <th>이름</th> / <td>${item.userNm}</td>
 *   {{JSP_DETAIL_ROWS}}  - 상세 tr 반복      예) <th>이름</th><td>${result.userNm}</td>
 *   {{JSP_FORM_INPUTS}}  - 등록/수정 input   예) <input type="text" name="userNm" />
 *   {{INSERT_VALUES}}    - INSERT VALUES     예) #{emplyrId}, #{userNm}
 *   {{UPDATE_SET}}       - UPDATE SET 절     예) USER_NM=#{userNm}, ...
 *   {{URL_PREFIX}}       - URL 접두사        예) /emp/employer
 *   {{DATE}}             - 작성일            예) 2026-05-15
 */
@Component
public class CodeTemplateTool {

    @Tool(description = """
            eGovFrame 5.x 표준 소스 코드 템플릿을 반환합니다.
            layer 파라미터에 생성할 레이어를 입력하세요:
              vo          → VO (Value Object)
              controller  → Spring MVC Controller
              service     → Service 인터페이스
              serviceImpl → ServiceImpl 구현체
              mapper      → MyBatis Mapper 인터페이스
              mapperXml   → MyBatis Mapper XML
              jspList     → 목록 JSP (Egov{{DOMAIN}}List.jsp)
              jspDetail   → 상세 JSP (Egov{{DOMAIN}}Detail.jsp)
              jspRegist   → 등록 JSP (Egov{{DOMAIN}}Regist.jsp)
              jspUpdt          → 수정 JSP (Egov{{DOMAIN}}Updt.jsp)
              controlleradvice → Validation 전역 예외 핸들러 (Egov{{DOMAIN}}ValidationHandler.java)

            [소스 생성 규칙 — 반드시 준수]
            1. buildFullCrudPrompt()가 제공한 플레이스홀더 값만 대입하세요.
            2. 템플릿에 없는 메서드, 주석, import를 추가하지 마세요.
            3. 플레이스홀더가 없는 줄은 한 글자도 변경하지 마세요.
            4. 클래스 선언·어노테이션·상속·패키지 구조를 그대로 유지하세요.
            5. import는 템플릿에 명시된 항목만 사용하세요.
            6. {{DOMAIN_KR}} 등 한국어 플레이스홀더는 buildFullCrudPrompt()의 값을 그대로 사용하고 임의 추론하지 마세요.
            """)
    public String getCodeTemplate(String layer) {
        return switch (layer.toLowerCase()) {
            case "vo"          -> voTemplate();
            case "controller"  -> controllerTemplate();
            case "service"     -> serviceTemplate();
            case "serviceimpl" -> serviceImplTemplate();
            case "mapper"      -> mapperTemplate();
            case "mapperxml"   -> mapperXmlTemplate();
            case "jsplist"     -> jspListTemplate();
            case "jspdetail"   -> jspDetailTemplate();
            case "jspregist"   -> jspRegistTemplate();
            case "jspupdt"           -> jspUpdtTemplate();
            case "controlleradvice"  -> controllerAdviceTemplate();
            default -> "지원하지 않는 레이어입니다. 사용 가능: vo, controller, service, serviceImpl, mapper, mapperXml, jspList, jspDetail, jspRegist, jspUpdt, controllerAdvice";
        };
    }

    private String voTemplate() {
        return """
                package {{PACKAGE}}.service;

                {{VALIDATION_IMPORT}}
                import lombok.Getter;
                import lombok.Setter;
                import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

                /**
                 * {{DOMAIN}} VO
                 * 테이블: {{TABLE_NAME}}
                 * @author Claude AI
                 * @since {{DATE}}
                 */
                @Getter
                @Setter
                public class {{DOMAIN}}VO {

                {{VO_FIELDS}}

                    // 페이징/검색 공통 필드
                    private int pageIndex = 1;
                    private int pageUnit = 10;
                    private int pageSize = 10;
                    private int firstIndex = 0;
                    private int lastIndex = 0;
                    private int recordCountPerPage = 10;
                    private String searchCondition = "";
                    private String searchKeyword = "";
                    private PaginationInfo paginationInfo;
                }
                """;
    }

    private String controllerTemplate() {
        return """
                package {{PACKAGE}}.web;

                import {{PACKAGE}}.service.{{DOMAIN}}Service;
                import {{PACKAGE}}.service.{{DOMAIN}}VO;
                import org.egovframe.rte.fdl.property.EgovPropertyService;
                import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Controller;
                import org.springframework.ui.ModelMap;
                import org.springframework.validation.BindingResult;
                import org.springframework.web.bind.annotation.ModelAttribute;
                import org.springframework.web.bind.annotation.RequestMapping;

                import jakarta.validation.Valid;
                import java.util.List;

                /**
                 * {{DOMAIN}} Controller
                 * @author Claude AI
                 * @since {{DATE}}
                 */
                @Controller
                @RequiredArgsConstructor
                public class Egov{{DOMAIN}}Controller {

                    private final {{DOMAIN}}Service {{DOMAIN_LC}}Service;
                    private final EgovPropertyService propertiesService;

                    /**
                     * {{DOMAIN}} 목록 조회
                     */
                    @RequestMapping("{{URL_PREFIX}}List.do")
                    public String select{{DOMAIN}}List(
                            @ModelAttribute("searchVO") {{DOMAIN}}VO searchVO,
                            ModelMap model) throws Exception {

                        searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
                        searchVO.setPageSize(propertiesService.getInt("pageSize"));

                        PaginationInfo paginationInfo = new PaginationInfo();
                        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
                        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
                        paginationInfo.setPageSize(searchVO.getPageSize());
                        searchVO.setPaginationInfo(paginationInfo);

                        int totCnt = {{DOMAIN_LC}}Service.select{{DOMAIN}}ListTotCnt(searchVO);
                        paginationInfo.setTotalRecordCount(totCnt);

                        List<{{DOMAIN}}VO> {{DOMAIN_LC}}List = {{DOMAIN_LC}}Service.select{{DOMAIN}}List(searchVO);
                        model.addAttribute("resultList", {{DOMAIN_LC}}List);
                        model.addAttribute("paginationInfo", paginationInfo);

                        return "{{DOMAIN_LC}}/Egov{{DOMAIN}}List";
                    }

                    /**
                     * {{DOMAIN}} 상세 조회
                     */
                    @RequestMapping("{{URL_PREFIX}}Detail.do")
                    public String select{{DOMAIN}}(
                            @ModelAttribute("searchVO") {{DOMAIN}}VO searchVO,
                            ModelMap model) throws Exception {

                        {{DOMAIN}}VO vo = {{DOMAIN_LC}}Service.select{{DOMAIN}}(searchVO);
                        model.addAttribute("result", vo);
                        return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Detail";
                    }

                    /**
                     * {{DOMAIN}} 등록 화면
                     */
                    @RequestMapping("{{URL_PREFIX}}RegistView.do")
                    public String insert{{DOMAIN}}View(
                            @ModelAttribute("searchVO") {{DOMAIN}}VO searchVO,
                            ModelMap model) throws Exception {
                        model.addAttribute("{{DOMAIN_LC}}VO", new {{DOMAIN}}VO());
                        return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Regist";
                    }

                    /**
                     * {{DOMAIN}} 등록
                     */
                    @RequestMapping("{{URL_PREFIX}}Regist.do")
                    public String insert{{DOMAIN}}(
                            @ModelAttribute("{{DOMAIN_LC}}VO") @Valid {{DOMAIN}}VO {{DOMAIN_LC}}VO,
                            BindingResult bindingResult,
                            ModelMap model) throws Exception {

                        if (bindingResult.hasErrors()) {
                            return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Regist";
                        }
                        {{DOMAIN_LC}}Service.insert{{DOMAIN}}({{DOMAIN_LC}}VO);
                        return "forward:{{URL_PREFIX}}List.do";
                    }

                    /**
                     * {{DOMAIN}} 수정 화면
                     */
                    @RequestMapping("{{URL_PREFIX}}UpdtView.do")
                    public String update{{DOMAIN}}View(
                            @ModelAttribute("searchVO") {{DOMAIN}}VO searchVO,
                            ModelMap model) throws Exception {

                        {{DOMAIN}}VO vo = {{DOMAIN_LC}}Service.select{{DOMAIN}}(searchVO);
                        model.addAttribute("{{DOMAIN_LC}}VO", vo);
                        return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Updt";
                    }

                    /**
                     * {{DOMAIN}} 수정
                     */
                    @RequestMapping("{{URL_PREFIX}}Updt.do")
                    public String update{{DOMAIN}}(
                            @ModelAttribute("{{DOMAIN_LC}}VO") @Valid {{DOMAIN}}VO {{DOMAIN_LC}}VO,
                            BindingResult bindingResult,
                            ModelMap model) throws Exception {

                        if (bindingResult.hasErrors()) {
                            return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Updt";
                        }
                        {{DOMAIN_LC}}Service.update{{DOMAIN}}({{DOMAIN_LC}}VO);
                        return "forward:{{URL_PREFIX}}List.do";
                    }

                    /**
                     * {{DOMAIN}} 삭제
                     */
                    @RequestMapping("{{URL_PREFIX}}Delete.do")
                    public String delete{{DOMAIN}}(
                            {{DOMAIN}}VO {{DOMAIN_LC}}VO,
                            ModelMap model) throws Exception {

                        {{DOMAIN_LC}}Service.delete{{DOMAIN}}({{DOMAIN_LC}}VO);
                        return "forward:{{URL_PREFIX}}List.do";
                    }
                }
                """;
    }

    private String serviceTemplate() {
        return """
                package {{PACKAGE}}.service;

                import java.util.List;

                /**
                 * {{DOMAIN}} Service 인터페이스
                 * @author Claude AI
                 * @since {{DATE}}
                 */
                public interface {{DOMAIN}}Service {

                    List<{{DOMAIN}}VO> select{{DOMAIN}}List({{DOMAIN}}VO searchVO) throws Exception;

                    int select{{DOMAIN}}ListTotCnt({{DOMAIN}}VO searchVO) throws Exception;

                    {{DOMAIN}}VO select{{DOMAIN}}({{DOMAIN}}VO searchVO) throws Exception;

                    void insert{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO) throws Exception;

                    void update{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO) throws Exception;

                    void delete{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO) throws Exception;
                }
                """;
    }

    private String serviceImplTemplate() {
        return """
                package {{PACKAGE}}.service.impl;

                import {{PACKAGE}}.service.{{DOMAIN}}Service;
                import {{PACKAGE}}.service.{{DOMAIN}}VO;
                import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;

                import java.util.List;

                /**
                 * {{DOMAIN}} ServiceImpl
                 * @author Claude AI
                 * @since {{DATE}}
                 */
                @Service("{{DOMAIN_LC}}Service")
                @RequiredArgsConstructor
                public class Egov{{DOMAIN}}ServiceImpl extends EgovAbstractServiceImpl
                        implements {{DOMAIN}}Service {

                    private final {{DOMAIN}}Mapper {{DOMAIN_LC}}Mapper;

                    @Override
                    public List<{{DOMAIN}}VO> select{{DOMAIN}}List({{DOMAIN}}VO searchVO) throws Exception {
                        return {{DOMAIN_LC}}Mapper.select{{DOMAIN}}List(searchVO);
                    }

                    @Override
                    public int select{{DOMAIN}}ListTotCnt({{DOMAIN}}VO searchVO) throws Exception {
                        return {{DOMAIN_LC}}Mapper.select{{DOMAIN}}ListTotCnt(searchVO);
                    }

                    @Override
                    public {{DOMAIN}}VO select{{DOMAIN}}({{DOMAIN}}VO searchVO) throws Exception {
                        return {{DOMAIN_LC}}Mapper.select{{DOMAIN}}(searchVO);
                    }

                    @Override
                    @Transactional
                    public void insert{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO) throws Exception {
                        {{DOMAIN_LC}}Mapper.insert{{DOMAIN}}({{DOMAIN_LC}}VO);
                    }

                    @Override
                    @Transactional
                    public void update{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO) throws Exception {
                        {{DOMAIN_LC}}Mapper.update{{DOMAIN}}({{DOMAIN_LC}}VO);
                    }

                    @Override
                    @Transactional
                    public void delete{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO) throws Exception {
                        {{DOMAIN_LC}}Mapper.delete{{DOMAIN}}({{DOMAIN_LC}}VO);
                    }
                }
                """;
    }

    private String mapperTemplate() {
        return """
                package {{PACKAGE}}.service.impl;

                import {{PACKAGE}}.service.{{DOMAIN}}VO;
                import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
                import org.apache.ibatis.annotations.Mapper;

                import java.util.List;

                /**
                 * {{DOMAIN}} Mapper
                 * @author Claude AI
                 * @since {{DATE}}
                 */
                @Mapper
                public interface {{DOMAIN}}Mapper extends EgovAbstractMapper {

                    List<{{DOMAIN}}VO> select{{DOMAIN}}List({{DOMAIN}}VO searchVO);

                    int select{{DOMAIN}}ListTotCnt({{DOMAIN}}VO searchVO);

                    {{DOMAIN}}VO select{{DOMAIN}}({{DOMAIN}}VO searchVO);

                    void insert{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO);

                    void update{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO);

                    void delete{{DOMAIN}}({{DOMAIN}}VO {{DOMAIN_LC}}VO);
                }
                """;
    }

    private String jspListTemplate() {
        return """
                <%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
                <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
                <%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
                <%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
                <%
                    String contextPath = request.getContextPath();
                %>
                <!DOCTYPE html>
                <html>
                <head>
                    <title>{{DOMAIN_KR}} 목록</title>
                </head>
                <body>
                <div class="container">
                    <h2>{{DOMAIN_KR}} 목록</h2>

                    <!-- 검색 -->
                    <form name="searchForm" action="<c:url value='{{URL_PREFIX}}List.do'/>" method="post">
                        <input type="hidden" name="pageIndex" value="${searchVO.pageIndex}"/>
                        <select name="searchCondition">
                            <option value="1">{{DOMAIN_KR}}ID</option>
                        </select>
                        <input type="text" name="searchKeyword" value="${searchVO.searchKeyword}"/>
                        <button type="submit">검색</button>
                    </form>

                    <!-- 목록 -->
                    <table>
                        <thead>
                        <tr>
                            <th>번호</th>
                            {{JSP_LIST_TH}}
                            <th>관리</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="item" items="${resultList}" varStatus="status">
                            <tr>
                                <td>${paginationInfo.totalRecordCount - ((searchVO.pageIndex-1) * searchVO.pageUnit) - status.index}</td>
                                {{JSP_LIST_TD}}
                                <td>
                                    <a href="<c:url value='{{URL_PREFIX}}Detail.do'/>?{{PK_FIELD}}=${item.{{PK_FIELD}}}">상세</a>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty resultList}">
                            <tr><td colspan="10">조회된 데이터가 없습니다.</td></tr>
                        </c:if>
                        </tbody>
                    </table>

                    <!-- 페이징 -->
                    <div>
                        <ui:pagination paginationInfo="${paginationInfo}"
                                       type="image"
                                       jsFunction="linkPage"/>
                    </div>

                    <!-- 버튼 -->
                    <div>
                        <a href="<c:url value='{{URL_PREFIX}}RegistView.do'/>">등록</a>
                    </div>
                </div>

                <script>
                function linkPage(pageNo) {
                    document.searchForm.pageIndex.value = pageNo;
                    document.searchForm.submit();
                }
                </script>
                </body>
                </html>
                """;
    }

    private String jspDetailTemplate() {
        return """
                <%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
                <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
                <%
                    String contextPath = request.getContextPath();
                %>
                <!DOCTYPE html>
                <html>
                <head>
                    <title>{{DOMAIN_KR}} 상세</title>
                </head>
                <body>
                <div class="container">
                    <h2>{{DOMAIN_KR}} 상세</h2>

                    <table>
                        <tbody>
                        {{JSP_DETAIL_ROWS}}
                        </tbody>
                    </table>

                    <!-- 버튼 -->
                    <div>
                        <a href="<c:url value='{{URL_PREFIX}}UpdtView.do'/>?{{PK_FIELD}}=${result.{{PK_FIELD}}}">수정</a>
                        <a href="<c:url value='{{URL_PREFIX}}List.do'/>">목록</a>
                        <form name="deleteForm" action="<c:url value='{{URL_PREFIX}}Delete.do'/>" method="post">
                            <input type="hidden" name="{{PK_FIELD}}" value="${result.{{PK_FIELD}}}"/>
                            <button type="button" onclick="if(confirm('삭제하시겠습니까?')) document.deleteForm.submit();">삭제</button>
                        </form>
                    </div>
                </div>
                </body>
                </html>
                """;
    }

    private String jspRegistTemplate() {
        return """
                <%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
                <%@ taglib prefix="c"    uri="http://java.sun.com/jsp/jstl/core"%>
                <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
                <!DOCTYPE html>
                <html>
                <head>
                    <title>{{DOMAIN_KR}} 등록</title>
                    <style>.error-msg { color: red; font-size: 0.85em; }</style>
                </head>
                <body>
                <div class="container">
                    <h2>{{DOMAIN_KR}} 등록</h2>

                    <form:form modelAttribute="{{DOMAIN_LC}}VO"
                               action="${pageContext.request.contextPath}{{URL_PREFIX}}Regist.do"
                               method="post">

                        <table>
                            <tbody>
                            {{JSP_FORM_INPUTS}}
                            </tbody>
                        </table>

                        <div>
                            <button type="submit">저장</button>
                            <a href="<c:url value='{{URL_PREFIX}}List.do'/>">취소</a>
                        </div>
                    </form:form>
                </div>
                </body>
                </html>
                """;
    }

    private String jspUpdtTemplate() {
        return """
                <%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
                <%@ taglib prefix="c"    uri="http://java.sun.com/jsp/jstl/core"%>
                <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
                <!DOCTYPE html>
                <html>
                <head>
                    <title>{{DOMAIN_KR}} 수정</title>
                    <style>.error-msg { color: red; font-size: 0.85em; }</style>
                </head>
                <body>
                <div class="container">
                    <h2>{{DOMAIN_KR}} 수정</h2>

                    <form:form modelAttribute="{{DOMAIN_LC}}VO"
                               action="${pageContext.request.contextPath}{{URL_PREFIX}}Updt.do"
                               method="post">
                        <form:hidden path="{{PK_FIELD}}"/>

                        <table>
                            <tbody>
                            {{JSP_FORM_INPUTS}}
                            </tbody>
                        </table>

                        <div>
                            <button type="submit">저장</button>
                            <a href="<c:url value='{{URL_PREFIX}}Detail.do'/>?{{PK_FIELD}}=${{{DOMAIN_LC}}VO.{{PK_FIELD}}}">취소</a>
                        </div>
                    </form:form>
                </div>
                </body>
                </html>
                """;
    }

    private String mapperXmlTemplate() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
                        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

                <mapper namespace="{{PACKAGE}}.service.impl.{{DOMAIN}}Mapper">

                    <resultMap id="{{DOMAIN_LC}}Map" type="{{PACKAGE}}.service.{{DOMAIN}}VO">
                {{RESULT_MAP_FIELDS}}
                    </resultMap>

                    <!-- 목록 조회 -->
                    <select id="select{{DOMAIN}}List" parameterType="{{PACKAGE}}.service.{{DOMAIN}}VO"
                            resultMap="{{DOMAIN_LC}}Map">
                        SELECT {{MAPPER_COLUMNS}}
                        FROM {{TABLE_NAME}}
                        <include refid="searchCondition"/>
                        ORDER BY {{PK_COLUMN}} DESC
                        LIMIT #{paginationInfo.firstRecordIndex}, #{recordCountPerPage}
                    </select>

                    <!-- 전체 건수 -->
                    <select id="select{{DOMAIN}}ListTotCnt" parameterType="{{PACKAGE}}.service.{{DOMAIN}}VO"
                            resultType="int">
                        SELECT COUNT(*)
                        FROM {{TABLE_NAME}}
                        <include refid="searchCondition"/>
                    </select>

                    <!-- 단건 조회 -->
                    <select id="select{{DOMAIN}}" parameterType="{{PACKAGE}}.service.{{DOMAIN}}VO"
                            resultMap="{{DOMAIN_LC}}Map">
                        SELECT {{MAPPER_COLUMNS}}
                        FROM {{TABLE_NAME}}
                        WHERE {{PK_COLUMN}} = #{{{PK_FIELD}}}
                    </select>

                    <!-- 등록 -->
                    <insert id="insert{{DOMAIN}}" parameterType="{{PACKAGE}}.service.{{DOMAIN}}VO">
                        INSERT INTO {{TABLE_NAME}} (
                            {{INSERT_COLUMNS}}
                        ) VALUES (
                            {{INSERT_VALUES}}
                        )
                    </insert>

                    <!-- 수정 -->
                    <update id="update{{DOMAIN}}" parameterType="{{PACKAGE}}.service.{{DOMAIN}}VO">
                        UPDATE {{TABLE_NAME}}
                        <set>
                {{UPDATE_SET}}
                        </set>
                        WHERE {{PK_COLUMN}} = #{{{PK_FIELD}}}
                    </update>

                    <!-- 삭제 -->
                    <delete id="delete{{DOMAIN}}" parameterType="{{PACKAGE}}.service.{{DOMAIN}}VO">
                        DELETE FROM {{TABLE_NAME}}
                        WHERE {{PK_COLUMN}} = #{{{PK_FIELD}}}
                    </delete>

                    <!-- 검색 조건 -->
                    <sql id="searchCondition">
                        <where>
                            <if test="searchKeyword != null and searchKeyword != ''">
                                <choose>
                                    <when test="searchCondition == '1'">
                                        AND {{PK_COLUMN}} LIKE CONCAT('%', #{searchKeyword}, '%')
                                    </when>
                                    <otherwise>
                                        AND {{PK_COLUMN}} LIKE CONCAT('%', #{searchKeyword}, '%')
                                    </otherwise>
                                </choose>
                            </if>
                        </where>
                    </sql>

                </mapper>
                """;
    }

    private String controllerAdviceTemplate() {
        return """
                package {{PACKAGE}}.web;

                import org.springframework.validation.FieldError;
                import org.springframework.web.bind.MethodArgumentNotValidException;
                import org.springframework.web.bind.annotation.ControllerAdvice;
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.servlet.mvc.support.RedirectAttributes;

                /**
                 * {{DOMAIN_KR}} Validation 전역 예외 핸들러
                 * BindingResult 없이 @Valid 검증 실패 시 HTTP 400 대신 에러 메시지와 함께 리다이렉트합니다.
                 * @author Claude AI
                 * @since {{DATE}}
                 */
                @ControllerAdvice(assignableTypes = Egov{{DOMAIN}}Controller.class)
                public class Egov{{DOMAIN}}ValidationHandler {

                    @ExceptionHandler(MethodArgumentNotValidException.class)
                    public String handleValidationError(
                            MethodArgumentNotValidException ex,
                            RedirectAttributes redirectAttributes) {

                        String message = ex.getBindingResult().getFieldErrors().stream()
                            .map(FieldError::getDefaultMessage)
                            .findFirst()
                            .orElse("입력값을 확인하세요.");
                        redirectAttributes.addFlashAttribute("errorMessage", message);
                        return "redirect:/error";
                    }
                }
                """;
    }
}
