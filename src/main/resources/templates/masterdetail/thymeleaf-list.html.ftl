<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${master.domainKr} 목록</title>
</head>
<th:block layout:fragment="content">
<#include "thymeleaf-list-body.html.ftl">
</th:block>

<th:block layout:fragment="scripts">
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
</th:block>
</html>
