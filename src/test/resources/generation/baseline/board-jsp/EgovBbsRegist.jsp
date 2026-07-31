<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<% String contextPath = request.getContextPath(); %>
<!DOCTYPE html>
<html>
<head>
    <title>BBS 등록</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<h1>BBS 등록</h1>
<form method="post" action="/bbs/bbsRegist.do">
    <c:if test="${not empty _csrf}">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
    </c:if>
    <input type="hidden" name="bbsId" value="${bbsVO.bbsId}"/>
    <table>
        <caption>BBS 등록 입력 폼</caption>
        <tr>
            <th>제목</th>
            <td><input type="text" name="nttSj" value="${bbsVO.nttSj}"/></td>
        </tr>
        <tr>
            <th>내용</th>
            <td><input type="text" name="nttCn" value="${bbsVO.nttCn}"/></td>
        </tr>
        <tr>
            <th>작성자명</th>
            <td><input type="text" name="ntcrNm" value="${bbsVO.ntcrNm}"/></td>
        </tr>
        <tr>
            <th>공지여부</th>
            <td><input type="text" name="noticeAt" value="${bbsVO.noticeAt}"/></td>
        </tr>
        <tr>
            <th>첨부파일ID</th>
            <td><input type="text" name="atchFileId" value="${bbsVO.atchFileId}"/></td>
        </tr>
        <tr>
            <th>최초등록시점</th>
            <td><input type="text" name="frstRegistPnttm" value="${bbsVO.frstRegistPnttm}"/></td>
        </tr>
    </table>
    <a href="/bbs/bbsList.do?bbsId=${bbsVO.bbsId}">취소</a>
    <button type="submit">저장</button>
</form>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
