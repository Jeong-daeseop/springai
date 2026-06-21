<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<!DOCTYPE html>
<html>
<head>
    <title>${domainKr} 목록</title>
</head>
<body>
<h1>${domainKr} 목록</h1>

<!-- 검색 -->
<form id="searchForm" method="get" action="${urlPrefix}List.do">
    <input type="hidden" name="bbsId" value="${r"${searchVO.bbsId}"}"/>
    <input type="hidden" name="pageIndex" value="${r"${searchVO.pageIndex}"}"/>
    <select name="searchCondition">
        <option value="1">제목</option>
        <option value="2">내용</option>
        <option value="3">작성자</option>
    </select>
    <input type="text" name="searchKeyword" value="${r"${searchVO.searchKeyword}"}"/>
    <button type="submit">검색</button>
</form>

<!-- 목록 -->
<table>
    <thead>
        <tr>
            <th>번호</th>
<#list listFields as f>
            <th>${f.comment}</th>
</#list>
            <th>등록일</th>
        </tr>
    </thead>
    <tbody>
        <c:forEach items="${r"${resultList}"}" var="item" varStatus="status">
        <tr>
            <td>${r"${status.count}"}</td>
<#list listFields as f>
            <td>
                <c:out value="${r"${item."}${f.javaName}${r"}"}"/>
            </td>
</#list>
            <td><c:out value="${r"${item.frstRegistPnttm}"}"/></td>
        </tr>
        </c:forEach>
    </tbody>
</table>

<!-- 페이지네이션 -->
<div>
    <ui:pagination paginationInfo="${r"${paginationInfo}"}"
                   type="image"
                   jsFunction="${domainLc}LinkPage"/>
</div>

<a href="${urlPrefix}RegistView.do">등록</a>

<script>
function ${domainLc}LinkPage(pageNo) {
    document.getElementById("searchForm").pageIndex.value = pageNo;
    document.getElementById("searchForm").submit();
}
</script>
</body>
</html>
