<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"    uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<%
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>EMPLYRINFO 수정</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<div class="container">
    <h2 class="page-title">EMPLYRINFO 수정</h2>

    <form:form modelAttribute="employerVO"
               action="${pageContext.request.contextPath}/emp/employerUpdt.do"
               method="post">
        <form:hidden path="emplyrId"/>
        <form:hidden path="frstRegistPnttm"/>
        <form:hidden path="lastUpdtPnttm"/>

        <div class="fieldset">
            <div class="form-group">
                <div class="form-tit">
                    <label>직원ID</label>
                </div>
                <div class="form-conts">
                    <span><c:out value="${employerVO.emplyrId}"/></span>
                </div>
            </div>
            <div class="form-group">
                <div class="form-tit">
                    <label for="emplyrNm">직원명</label>
                </div>
                <div class="form-conts">
                    <form:input path="emplyrNm" id="emplyrNm" cssClass="krds-input"
                                maxlength="50"
                                placeholder="직원명을(를) 입력하세요"/>
                    <form:errors path="emplyrNm" cssClass="form-hint-invalid" element="p"/>
                </div>
            </div>
            <div class="form-group">
                <div class="form-tit">
                    <label for="emailAdres">이메일주소</label>
                </div>
                <div class="form-conts">
                    <form:input path="emailAdres" id="emailAdres" cssClass="krds-input"
                                maxlength="100"
                                placeholder="이메일주소을(를) 입력하세요"/>
                    <form:errors path="emailAdres" cssClass="form-hint-invalid" element="p"/>
                </div>
            </div>
            <div class="form-group">
                <div class="form-tit">
                    <label for="ofcpsNm">직급명</label>
                </div>
                <div class="form-conts">
                    <form:input path="ofcpsNm" id="ofcpsNm" cssClass="krds-input"
                                maxlength="50"
                                placeholder="직급명을(를) 입력하세요"/>
                    <form:errors path="ofcpsNm" cssClass="form-hint-invalid" element="p"/>
                </div>
            </div>
        </div>

        <!-- 버튼 -->
        <div class="btn-area">
            <a href="<c:url value='/emp/employerDetail.do'/>?emplyrId=${employerVO.emplyrId}"
               class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">저장</button>
        </div>
    </form:form>
</div>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
<%-- @region:protected:customSection start --%>
<%-- 이 위치의 커스텀 마크업/스크립트는 재생성 시 보존됩니다. --%>
<%-- @region:protected:customSection end --%>
</body>
</html>
