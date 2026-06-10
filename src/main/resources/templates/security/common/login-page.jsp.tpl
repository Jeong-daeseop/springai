<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c"      uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>로그인 | 전자정부 표준프레임워크</title>
</head>
<body>

<!-- ============================================================
     eGovFrame 표준 로그인 폼
     action: context-security.xml login-processing-url 과 일치
     CSRF 토큰: Spring Security CSRF 활성화 시 필수
============================================================ -->
<form action="<c:url value='/uat/uia/actionLogin.do'/>" method="post">

    <!-- CSRF 토큰 (Spring Security 기본 활성화 — 반드시 포함) -->
    <input type="hidden"
           name="${_csrf.parameterName}"
           value="${_csrf.token}"/>

    <!-- 로그인 실패 메시지 -->
    <c:if test="${param.login_error == '1'}">
        <p style="color:red;">
            아이디 또는 비밀번호가 올바르지 않습니다.
        </p>
    </c:if>

    <!-- 세션 만료 메시지 -->
    <c:if test="${param.expired == '1'}">
        <p style="color:red;">
            다른 기기에서 로그인되어 세션이 만료되었습니다.
        </p>
    </c:if>

    <div>
        <label for="j_username">아이디</label>
        <input type="text" id="j_username" name="j_username"
               autocomplete="username" required/>
    </div>
    <div>
        <label for="j_password">비밀번호</label>
        <input type="password" id="j_password" name="j_password"
               autocomplete="current-password" required/>
    </div>

    <button type="submit">로그인</button>

</form>

<!--
[참고] input name 매핑
  j_username → eGovFrame 관례 파라미터명
               (Spring Security 기본값: username — .usernameParameter("j_username")으로 명시 설정됨)
  j_password → eGovFrame 관례 파라미터명
               (Spring Security 기본값: password — .passwordParameter("j_password")으로 명시 설정됨)

[참고] CSRF 비활성화 시 (REST API 서버 등)
  .csrf(csrf -> csrf.disable()) 설정 후 해당 hidden input 제거 가능
  JSP 기반 공공 SI 환경에서는 CSRF 반드시 활성화 유지
-->

</body>
</html>
