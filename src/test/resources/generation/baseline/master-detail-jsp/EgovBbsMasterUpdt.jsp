<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>BBSMASTER 수정</title>
</head>
<body>
<h1>BBSMASTER 수정</h1>
<form:form modelAttribute="bbsMasterVO" action="/bbs/bbsMasterUpdt.do" method="post">
    <form:hidden path="bbsId"/>
    <table border="1">
        <tr>
            <th>게시판ID</th>
            <td><c:out value="${bbsMasterVO.bbsId}"/></td>
        </tr>
        <tr>
            <th>게시판명</th>
            <td>
                <form:input path="bbsNm"/>
                <form:errors path="bbsNm"/>
            </td>
        </tr>
        <tr>
            <th>게시판소개</th>
            <td>
                <form:input path="bbsIntrcn"/>
                <form:errors path="bbsIntrcn"/>
            </td>
        </tr>
    </table>
    <button type="submit">저장</button>
    <a href="/bbs/bbsMasterDetail.do?bbsId=${bbsMasterVO.bbsId}">취소</a>
</form:form>
</body>
</html>
