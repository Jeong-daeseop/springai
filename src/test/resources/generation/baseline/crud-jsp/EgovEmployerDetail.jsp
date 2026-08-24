<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>EMPLYRINFO 상세</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<div class="container">
    <h2 class="page-title">EMPLYRINFO 상세</h2>

    <c:if test="${not empty message}">
    <div class="alert alert-success">
        <c:out value="${message}"/>
    </div>
    </c:if>

    <div class="krds-table-wrap">
        <table class="tbl col">
            <caption>EMPLYRINFO 상세 정보</caption>
            <colgroup>
                <col class="egov-detail-label-col">
                <col>
            </colgroup>
            <tbody>
            <tr>
                <th scope="row">직원ID</th>
                <td><c:out value="${result.emplyrId}"/></td>
            </tr>
            <tr>
                <th scope="row">직원명</th>
                <td><c:out value="${result.emplyrNm}"/></td>
            </tr>
            <tr>
                <th scope="row">이메일주소</th>
                <td><c:out value="${result.emailAdres}"/></td>
            </tr>
            <tr>
                <th scope="row">직급명</th>
                <td><c:out value="${result.ofcpsNm}"/></td>
            </tr>
            <tr>
                <th scope="row">최초등록시점</th>
                <td><c:out value="${result.frstRegistPnttm}"/></td>
            </tr>
            <tr>
                <th scope="row">최종수정시점</th>
                <td><c:out value="${result.lastUpdtPnttm}"/></td>
            </tr>
            </tbody>
        </table>
    </div>

    <!-- 버튼 -->
    <div class="btn-area">
        <a href="<c:url value='/emp/employerList.do'/>" class="krds-btn secondary medium">목록</a>
        <a href="<c:url value='/emp/employerUpdtView.do'/>?emplyrId=${result.emplyrId}"
           class="krds-btn primary medium">수정</a>
        <form name="deleteForm" action="<c:url value='/emp/employerDelete.do'/>" method="post" class="egov-jsp-inline-form">
            <input type="hidden" name="emplyrId" value="${result.emplyrId}"/>
            <button type="button" class="krds-btn tertiary medium"
                    onclick="if(confirm('삭제하시겠습니까?')) document.deleteForm.submit();">삭제</button>
        </form>
    </div>
</div>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
<%-- @region:protected:customSection start --%>
<%-- 이 위치의 커스텀 마크업/스크립트는 재생성 시 보존됩니다. --%>
<%-- @region:protected:customSection end --%>
</body>
</html>
