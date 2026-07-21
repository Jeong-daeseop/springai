<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>${master.domainKr} 목록</title>
    <link rel="stylesheet" th:href="@{/resources/css/styles.css}">
</head>
<body>
<div class="egov-standalone-shell egov-crud-page">
<#include "thymeleaf-list-body.html.ftl">
</div>
<script th:src="@{/resources/js/krds.min.js}"></script>
<script>
document.querySelectorAll('tr[data-row-link="true"][data-href]').forEach(function(row) {
    row.addEventListener('click', function() {
        window.location.href = this.dataset.href;
    });
});

function toggleAll(chk) {
    document.querySelectorAll('.row-check').forEach(function(c) { c.checked = chk.checked; });
    updateBulkDeleteBtn();
}
document.querySelectorAll('.row-check').forEach(function(c) {
    c.addEventListener('change', updateBulkDeleteBtn);
});
function updateBulkDeleteBtn() {
    var checked = document.querySelectorAll('.row-check:checked').length;
    document.getElementById('btnBulkDelete').style.display = checked > 0 ? '' : 'none';
}

function openRowDeleteModal(id) {
    document.getElementById('rowDeleteId').value = id;
    document.getElementById('rowDeleteModal').showModal();
}

function openBulkDeleteModal() {
    var form = document.getElementById('bulkDeleteForm');
    form.querySelectorAll('input[name="deleteIds"]').forEach(function(el) { el.remove(); });
    document.querySelectorAll('.row-check:checked').forEach(function(c) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'deleteIds';
        input.value = c.value;
        form.appendChild(input);
    });
    document.getElementById('bulkDeleteModal').showModal();
}
</script>
</body>
</html>
