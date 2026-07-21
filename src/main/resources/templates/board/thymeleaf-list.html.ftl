<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${displayName} 목록</title>
</head>
<section layout:fragment="content" class="egov-crud-page">
<#include "thymeleaf-list-body.html.ftl">
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
