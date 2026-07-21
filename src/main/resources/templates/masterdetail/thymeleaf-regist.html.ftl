<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${master.domainKr} 등록</title>
</head>
<section layout:fragment="content" class="egov-crud-page">
<#include "thymeleaf-regist-body.html.ftl">
</section>
</html>
