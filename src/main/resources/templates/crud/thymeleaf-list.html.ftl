<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${domainKr} 목록</title>
</head>
<section layout:fragment="content" class="egov-crud-page">
<#include "thymeleaf-list-body.html.ftl">
<!--/* @region:protected:customSection start */-->
<!--/* 이 위치의 커스텀 마크업은 재생성 시 보존됩니다. */-->
<!--/* @region:protected:customSection end */-->
</section>

<th:block layout:fragment="scripts">
<script>
document.querySelectorAll('tr[data-row-link="true"][data-href]').forEach(function(row) {
    row.addEventListener('click', function() {
        window.location.href = this.dataset.href;
    });
});
</script>
</th:block>
</html>
