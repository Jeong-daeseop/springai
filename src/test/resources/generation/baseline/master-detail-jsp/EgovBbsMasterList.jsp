<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>BBSMASTER 목록</title>
</head>
<body>
<h1>BBSMASTER 목록</h1>
<form action="/bbs/bbsMasterList.do" method="get">
    <input type="text" name="searchKeyword" value="<c:out value="${searchVO.searchKeyword}"/>">
    <button type="submit">검색</button>
    <a href="/bbs/bbsMasterRegistView.do">등록</a>
</form>
<table border="1">
    <thead>
    <tr>
        <th>게시판ID</th>
        <th>게시판명</th>
        <th>게시판소개</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="item" items="${resultList}">
        <tr>
            <td><a href="/bbs/bbsMasterDetail.do?bbsId=${item.bbsId}"><c:out value="${item.bbsId}"/></a></td>
            <td><c:out value="${item.bbsNm}"/></td>
            <td><c:out value="${item.bbsIntrcn}"/></td>
        </tr>
    </c:forEach>
    </tbody>
</table>
</body>
</html>
