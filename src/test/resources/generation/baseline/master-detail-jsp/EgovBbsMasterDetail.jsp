<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>BBSMASTER 상세</title>
</head>
<body>
<h1>BBSMASTER 상세</h1>
<table border="1">
    <tr>
        <th>게시판ID</th>
        <td><c:out value="${result.bbsId}"/></td>
    </tr>
    <tr>
        <th>게시판명</th>
        <td><c:out value="${result.bbsNm}"/></td>
    </tr>
    <tr>
        <th>게시판소개</th>
        <td><c:out value="${result.bbsIntrcn}"/></td>
    </tr>
</table>

<h2>BBSUSE 목록</h2>
<table border="1">
    <thead>
    <tr>
        <th>게시판ID</th>
        <th>사용여부</th>
        <th>전송대상분류</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="detail" items="${detailList}">
        <tr>
            <td><c:out value="${detail.bbsId}"/></td>
            <td><c:out value="${detail.useAt}"/></td>
            <td><c:out value="${detail.sendTargetClassify}"/></td>
        </tr>
    </c:forEach>
    </tbody>
</table>
<form action="/bbs/bbsMasterDelete.do" method="post" onsubmit="return confirm('삭제하시겠습니까?');">
    <input type="hidden" name="bbsId" value="${result.bbsId}">
    <button type="submit">삭제</button>
    <a href="/bbs/bbsMasterList.do">목록</a>
</form>
</body>
</html>
