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
    <title>EMPLYRINFO 등록</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<div class="container">
    <h2 class="page-title">EMPLYRINFO 등록</h2>

    <form:form modelAttribute="employerVO"
               action="${pageContext.request.contextPath}/emp/employerRegist.do"
               method="post">

        <div class="fieldset">
            <div class="form-group">
                <div class="form-tit">
                    <label for="emplyrId">직원ID</label>
                </div>
                <div class="form-conts">
                    <form:input path="emplyrId" id="emplyrId" cssClass="krds-input"
                                maxlength="20"
                                placeholder="직원ID을(를) 입력하세요"/>
                    <form:errors path="emplyrId" cssClass="form-hint-invalid" element="p"/>
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
            <a href="<c:url value='/emp/employerList.do'/>" class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">저장</button>
        </div>
    </form:form>
</div>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
