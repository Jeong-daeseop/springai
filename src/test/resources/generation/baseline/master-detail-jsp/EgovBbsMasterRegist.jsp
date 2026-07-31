<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>BBSMASTER 등록</title>
</head>
<body>
<h1>BBSMASTER 등록</h1>
<form:form modelAttribute="bbsMasterVO" action="/bbs/bbsMasterRegist.do" method="post">
    <table border="1">
        <tr>
            <th>게시판ID</th>
            <td>
                <form:input path="bbsId"/>
                <form:errors path="bbsId"/>
            </td>
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
    <a href="/bbs/bbsMasterList.do">취소</a>
</form:form>
</body>
</html>
