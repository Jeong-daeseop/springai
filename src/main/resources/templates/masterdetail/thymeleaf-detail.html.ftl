<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${master.domainKr} 상세</title>
</head>
<th:block layout:fragment="content">
    <th:block th:replace="~{layout/breadcrumb :: breadcrumb}"></th:block>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:16px;margin-bottom:20px;border-bottom:2px solid #1e2124;">
        <h1>${master.domainKr} 상세</h1>
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
        <h2 style="display:flex;align-items:center;gap:7px;margin:0 0 12px;font-size:16px;font-weight:800;color:#083891;">${master.domainKr} 정보</h2>
        <div class="krds-table-wrap">
            <table class="tbl col">
                <caption>${master.domainKr} 상세 정보</caption>
                <tbody>
<#list master.fields as f>
<#assign isStatus = f.javaName == "useAt" || f.javaName == "useYn" || f.javaName == "sttus" || f.javaName == "status" || f.javaName == "activeYn">
                <tr>
                    <th scope="row">${f.comment}</th>
                    <td>
<#if isStatus>
                        <span th:if="${'$'}{result.${f.javaName} == 'Y'}"
                              style="display:inline-flex;align-items:center;height:22px;padding:0 8px;border-radius:12px;background:#e8f5e9;color:#2e7d32;font-size:12px;font-weight:700;">사용</span>
                        <span th:unless="${'$'}{result.${f.javaName} == 'Y'}"
                              style="display:inline-flex;align-items:center;height:22px;padding:0 8px;border-radius:12px;background:#fce4ec;color:#c62828;font-size:12px;font-weight:700;">중지</span>
<#else>
                        <span th:text="${'$'}{result.${f.javaName}}"></span>
</#if>
                    </td>
                </tr>
</#list>
                </tbody>
            </table>
        </div>
    </section>

    <section style="margin-bottom:32px;">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">
            <h2 style="display:flex;align-items:center;gap:7px;margin:0;font-size:16px;font-weight:800;color:#083891;">
                ${detail.domainKr} 목록
                <span th:if="${'$'}{detailList != null}"
                      style="font-size:13px;font-weight:400;color:#8a949e;"
                      th:text="'총 ' + ${'$'}{#lists.size(detailList)} + '건'">총 0건</span>
            </h2>
            <a th:href="@{${urlPrefix}${detail.domainLc?cap_first}RegistView.do(${master.pk.javaName}=${'$'}{result.${master.pk.javaName}})}"
               class="krds-btn primary small">
                <span aria-hidden="true">＋</span>
                ${detail.domainKr} 등록
            </a>
        </div>
        <div class="krds-table-wrap">
            <table class="tbl col">
                <caption>${detail.domainKr} 목록</caption>
                <thead>
                <tr>
<#list detail.fields as f>
                    <th scope="col">${f.comment}</th>
</#list>
                    <th scope="col">관리</th>
                </tr>
                </thead>
                <tbody>
                <tr th:each="detailItem : ${'$'}{detailList}">
<#list detail.fields as f>
<#assign isStatus = f.javaName == "useAt" || f.javaName == "useYn" || f.javaName == "sttus" || f.javaName == "status" || f.javaName == "activeYn">
                    <td>
<#if isStatus>
                        <span th:if="${'$'}{detailItem.${f.javaName} == 'Y'}"
                              style="display:inline-flex;align-items:center;height:22px;padding:0 8px;border-radius:12px;background:#e8f5e9;color:#2e7d32;font-size:12px;font-weight:700;">사용</span>
                        <span th:unless="${'$'}{detailItem.${f.javaName} == 'Y'}"
                              style="display:inline-flex;align-items:center;height:22px;padding:0 8px;border-radius:12px;background:#fce4ec;color:#c62828;font-size:12px;font-weight:700;">중지</span>
<#else>
                        <span th:text="${'$'}{detailItem.${f.javaName}}"></span>
</#if>
                    </td>
</#list>
                    <td style="text-align:center;">
                        <a th:href="@{${urlPrefix}${detail.domainLc?cap_first}UpdtView.do(${master.pk.javaName}=${'$'}{result.${master.pk.javaName}},${detail.pk.javaName}=${'$'}{detailItem.${detail.pk.javaName}})}"
                           class="krds-btn secondary small">수정</a>
                    </td>
                </tr>
                <tr th:if="${'$'}{#lists.isEmpty(detailList)}">
                    <td colspan="${detail.fields?size + 1}"
                        style="padding:32px 0;text-align:center;color:#8a949e;">
                        등록된 ${detail.domainKr} 정보가 없습니다.
                    </td>
                </tr>
                </tbody>
            </table>
        </div>
    </section>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:28px;">
        <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">목록</a>
        <div style="display:flex;gap:8px;">
            <a th:href="@{${urlPrefix}UpdtView.do(${master.pk.javaName}=${'$'}{result.${master.pk.javaName}})}"
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
        <input type="hidden" name="${master.pk.javaName}" th:value="${'$'}{result.${master.pk.javaName}}"/>
    </form>
</th:block>
</html>
