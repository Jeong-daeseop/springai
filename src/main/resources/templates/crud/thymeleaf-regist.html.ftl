<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${domainKr} 등록</title>
</head>
<section layout:fragment="content">
<#include "thymeleaf-regist-body.html.ftl">
</section>
</html>
