<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"      uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="ui"     uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${domainKr} 목록</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/krds.min.css">
</head>
<body>
<div class="container">
    <h2 class="page-title">${domainKr} 목록</h2>

    <c:if test="${'$'}{not empty message}">
    <div class="alert alert-success">
        <c:out value="${'$'}{message}"/>
    </div>
    </c:if>

    <!-- 검색 -->
    <div class="fieldset">
        <form name="searchForm" action="<c:url value='${urlPrefix}List.do'/>" method="get">
            <input type="hidden" name="pageIndex" value="${'$'}{searchVO.pageIndex}"/>
            <div class="form-group">
                <div class="form-conts keyword-sch">
                    <div class="sch-form-wrap">
                        <div class="input-group">
                            <select name="searchCondition" class="krds-form-select medium" title="검색조건">
                                <option value="1">${domainKr}ID</option>
                            </select>
                            <div class="sch-input">
                                <input type="text" name="searchKeyword" class="krds-input medium"
                                       value="${'$'}{searchVO.searchKeyword}" placeholder="검색어를 입력하세요"/>
                            </div>
                            <button type="submit" class="krds-btn primary medium">검색</button>
                        </div>
                    </div>
                </div>
            </div>
        </form>
    </div>

    <!-- 목록 -->
    <div class="krds-structured-list-table">
        <div class="search-list-top">
            <div class="sch-left"></div>
            <div class="sch-right">
                <a href="<c:url value='${urlPrefix}RegistView.do'/>" class="krds-btn primary medium">등록</a>
            </div>
        </div>

        <div class="krds-table-wrap">
            <table class="tbl col data">
                <caption>${domainKr} 목록 표</caption>
                <colgroup>
                    <col style="width: 8%;">
<#list listFields as f>
                    <col>
</#list>
                    <col style="width: 10%;">
                </colgroup>
                <thead>
                <tr>
                    <th scope="col">번호</th>
<#list listFields as f>
                    <th scope="col">${f.comment}</th>
</#list>
                    <th scope="col">관리</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="item" items="${'$'}{resultList}" varStatus="status">
                    <tr>
                        <td>${'$'}{paginationInfo.totalRecordCount - ((searchVO.pageIndex-1) * searchVO.pageUnit) - status.index}</td>
<#list listFields as f>
                        <td><c:out value="${'$'}{item.${f.javaName}}"/></td>
</#list>
                        <td>
                            <a href="<c:url value='${urlPrefix}Detail.do'/>?${pk.javaName}=${'$'}{item.${pk.javaName}}"
                               class="krds-btn secondary xsmall">상세</a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${'$'}{empty resultList}">
                    <tr>
                        <td colspan="${listFields?size + 2}" class="no-data">조회된 데이터가 없습니다.</td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>

        <!-- 페이징 -->
        <div class="krds-pagination">
            <ui:pagination paginationInfo="${'$'}{paginationInfo}"
                           type="image"
                           jsFunction="linkPage"/>
        </div>
    </div>
</div>

<script>
function linkPage(pageNo) {
    document.searchForm.pageIndex.value = pageNo;
    document.searchForm.submit();
}
</script>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
