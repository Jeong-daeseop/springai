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
    <title>${domainKr} 목록</title>
</head>
<body>
<div class="container">
    <h2>${domainKr} 목록</h2>

    <!-- 검색 -->
    <form name="searchForm" action="<c:url value='${urlPrefix}List.do'/>" method="post">
        <input type="hidden" name="pageIndex" value="${'$'}{searchVO.pageIndex}"/>
        <select name="searchCondition">
            <option value="1">${domainKr}ID</option>
        </select>
        <input type="text" name="searchKeyword" value="${'$'}{searchVO.searchKeyword}"/>
        <button type="submit">검색</button>
    </form>

    <!-- 목록 -->
    <table>
        <thead>
        <tr>
            <th>번호</th>
<#list fields as f>
            <th>${f.comment}</th>
</#list>
            <th>관리</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="item" items="${'$'}{resultList}" varStatus="status">
            <tr>
                <td>${'$'}{paginationInfo.totalRecordCount - ((searchVO.pageIndex-1) * searchVO.pageUnit) - status.index}</td>
<#list fields as f>
                <td><c:out value="${'$'}{item.${f.javaName}}"/></td>
</#list>
                <td>
                    <a href="<c:url value='${urlPrefix}Detail.do'/>?${pk.javaName}=${'$'}{item.${pk.javaName}}">상세</a>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${'$'}{empty resultList}">
            <tr><td colspan="${fields?size + 2}">조회된 데이터가 없습니다.</td></tr>
        </c:if>
        </tbody>
    </table>

    <!-- 페이징 -->
    <div>
        <ui:pagination paginationInfo="${'$'}{paginationInfo}"
                       type="image"
                       jsFunction="linkPage"/>
    </div>

    <!-- 버튼 -->
    <div>
        <a href="<c:url value='${urlPrefix}RegistView.do'/>">등록</a>
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
