<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<% String contextPath = request.getContextPath(); %>
<!DOCTYPE html>
<html>
<head>
    <title>BBS 상세</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<h1>BBS 상세</h1>
<table>
    <caption>BBS 상세 정보</caption>
    <tbody>
        <tr>
            <th>게시판ID</th>
            <td><c:out value="${result.bbsId}"/></td>
        </tr>
        <tr>
            <th>게시글번호</th>
            <td><c:out value="${result.nttId}"/></td>
        </tr>
        <tr>
            <th>제목</th>
            <td><c:out value="${result.nttSj}"/></td>
        </tr>
        <tr>
            <th>내용</th>
            <td><c:out value="${result.nttCn}"/></td>
        </tr>
        <tr>
            <th>작성자명</th>
            <td><c:out value="${result.ntcrNm}"/></td>
        </tr>
        <tr>
            <th>공지여부</th>
            <td><c:out value="${result.noticeAt}"/></td>
        </tr>
        <tr>
            <th>첨부파일ID</th>
            <td><c:out value="${result.atchFileId}"/></td>
        </tr>
        <tr>
            <th>최초등록시점</th>
            <td><c:out value="${result.frstRegistPnttm}"/></td>
        </tr>
        <tr>
            <th>조회수</th>
            <td><c:out value="${result.rdcnt}"/></td>
        </tr>
    </tbody>
</table>

<h3>첨부파일</h3>
<ul>
    <c:forEach var="file" items="${fileList}">
        <li><a href="<c:url value='/cmm/fms/FileDown.do'><c:param name='atchFileId' value='${file.atchFileId}'/><c:param name='fileSn' value='${file.fileSn}'/></c:url>"><c:out value="${file.originalFileName}"/></a> (<c:out value="${file.fileSize}"/> bytes)</li>
    </c:forEach>
    <c:if test="${empty fileList}"><li>첨부파일 없음</li></c:if>
</ul>

<a href="/bbs/bbsList.do?bbsId=${result.bbsId}">목록</a>
<a href="/bbs/bbsUpdtView.do?bbsId=${result.bbsId}&nttId=${result.nttId}">수정</a>
<form method="post" action="/bbs/bbsDelete.do" class="egov-jsp-inline-form">
    <c:if test="${not empty _csrf}">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
    </c:if>
    <input type="hidden" name="bbsId" value="${result.bbsId}"/>
    <input type="hidden" name="nttId" value="${result.nttId}"/>
    <button type="submit" onclick="return confirm('삭제하시겠습니까?')">삭제</button>
</form>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
