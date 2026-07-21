<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${domainKr} 수정</title>
</head>
<section layout:fragment="content" class="egov-crud-page">
<#include "thymeleaf-updt-body.html.ftl">
</section>
</html>
