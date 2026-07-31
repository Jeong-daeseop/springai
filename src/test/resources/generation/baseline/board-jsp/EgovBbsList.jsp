<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<% String contextPath = request.getContextPath(); %>
<!DOCTYPE html>
<html>
<head>
    <title>BBS 목록</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<h1>BBS 목록</h1>

<!-- 검색 -->
<form id="searchForm" method="get" action="/bbs/bbsList.do">
    <input type="hidden" name="bbsId" value="${searchVO.bbsId}"/>
    <input type="hidden" name="pageIndex" value="${searchVO.pageIndex}"/>
    <select name="searchCondition">
        <option value="1">제목</option>
        <option value="2">내용</option>
        <option value="3">작성자</option>
    </select>
    <input type="text" name="searchKeyword" value="${searchVO.searchKeyword}"/>
    <button type="submit">검색</button>
</form>

<!-- 목록 -->
<table>
    <caption>BBS 목록 표</caption>
    <thead>
        <tr>
            <th>번호</th>
            <th>공지여부</th>
            <th>게시글번호</th>
            <th>제목</th>
            <th>작성자명</th>
            <th>최초등록시점</th>
            <th>게시판ID</th>
            <th>등록일</th>
        </tr>
    </thead>
    <tbody>
        <c:forEach items="${resultList}" var="item" varStatus="status">
        <tr>
            <td>${status.count}</td>
            <td>
                <c:out value="${item.noticeAt}"/>
            </td>
            <td>
                <c:out value="${item.nttId}"/>
            </td>
            <td>
                <c:out value="${item.nttSj}"/>
            </td>
            <td>
                <c:out value="${item.ntcrNm}"/>
            </td>
            <td>
                <c:out value="${item.frstRegistPnttm}"/>
            </td>
            <td>
                <c:out value="${item.bbsId}"/>
            </td>
            <td><c:out value="${item.frstRegistPnttm}"/></td>
        </tr>
        </c:forEach>
    </tbody>
</table>

<!-- 페이지네이션 -->
<div>
    <ui:pagination paginationInfo="${paginationInfo}"
                   type="image"
                   jsFunction="bbsLinkPage"/>
</div>

<a href="/bbs/bbsRegistView.do?bbsId=${searchVO.bbsId}">등록</a>

<script>
function bbsLinkPage(pageNo) {
    document.getElementById("searchForm").pageIndex.value = pageNo;
    document.getElementById("searchForm").submit();
}
</script>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
