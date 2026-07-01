<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 상세</title>
</head>
<section layout:fragment="content">
    <th:block th:replace="~{layout/breadcrumb :: breadcrumb}"></th:block>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:16px;margin-bottom:20px;border-bottom:2px solid #1e2124;">
        <h1>${domainKr} 상세</h1>
        <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">목록</a>
    </div>

    <div id="toast-alert" th:if="${'$'}{message}"
         style="position:fixed;bottom:32px;left:50%;transform:translateX(-50%);display:flex;align-items:center;gap:10px;padding:14px 20px;border-radius:8px;background:#1e2124;color:#fff;font-size:14px;font-weight:600;z-index:9999;box-shadow:0 4px 16px rgba(0,0,0,.18);white-space:nowrap;"
         role="alert" aria-live="polite">
        <span aria-hidden="true" style="color:#5cb85c;">✓</span>
        <span th:text="${'$'}{message}">처리되었습니다.</span>
    </div>
    <script>
    (function(){var t=document.getElementById('toast-alert');if(t){setTimeout(function(){t.style.transition='opacity 0.4s';t.style.opacity='0';setTimeout(function(){t.remove();},400);},3000);}})();
    </script>

    <section style="margin-bottom:32px;">
        <h2 style="display:flex;align-items:center;gap:7px;margin:0 0 12px;font-size:16px;font-weight:800;color:#083891;">${domainKr} 정보</h2>
        <div class="krds-table-wrap">
            <table class="tbl col">
                <caption>${domainKr} 상세 정보</caption>
                <tbody>
<#list fields as f>
                <tr>
                    <th scope="row">${f.comment}</th>
                    <td th:text="${'$'}{result.${f.javaName}}"></td>
                </tr>
</#list>
                </tbody>
            </table>
        </div>
    </section>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:28px;">
        <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">목록</a>
        <div style="display:flex;gap:8px;">
            <a th:href="@{${urlPrefix}UpdtView.do(${pk.javaName}=${'$'}{result.${pk.javaName}})}"
               class="krds-btn primary medium">수정</a>
            <button type="button" class="krds-btn negative medium"
                    onclick="document.getElementById('deleteModal').showModal()">삭제</button>
        </div>
    </div>

    <dialog id="deleteModal"
            style="border:none;border-radius:12px;padding:32px;min-width:320px;max-width:400px;box-shadow:0 8px 32px rgba(0,0,0,.18);">
        <h2 style="margin:0 0 10px;font-size:18px;font-weight:800;color:#1e2124;">삭제 확인</h2>
        <p style="margin:0 0 24px;font-size:14px;color:#58616a;line-height:1.6;">삭제하시겠습니까?<br>삭제된 데이터는 복구할 수 없습니다.</p>
        <div style="display:flex;justify-content:flex-end;gap:8px;">
            <button type="button" class="krds-btn secondary medium"
                    onclick="document.getElementById('deleteModal').close()">취소</button>
            <button type="button" class="krds-btn negative medium"
                    onclick="document.getElementById('deleteForm').submit()">삭제</button>
        </div>
    </dialog>
    <form id="deleteForm" th:action="@{${urlPrefix}Delete.do}" method="post" style="display:none;">
        <input type="hidden" name="${pk.javaName}" th:value="${'$'}{result.${pk.javaName}}"/>
    </form>
</section>
</html>
