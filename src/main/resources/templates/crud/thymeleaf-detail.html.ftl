<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${domainKr} 상세</title>
</head>
<section layout:fragment="content" class="egov-crud-page">
<#include "thymeleaf-detail-body.html.ftl">
</section>
</html>
