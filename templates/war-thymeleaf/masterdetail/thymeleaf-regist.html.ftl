<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<#--
  =====================================================================
  eGovFrame SpringAI MCP CrudPromptBuilderTool
  Template : masterdetail/thymeleaf-regist.html.ftl
  Design   : FTC 스타일 (KRDS) — CRUD 마스터 등록/수정형
  =====================================================================
  FreeMarker 변수
    mClassLabel  : 마스터 화면 한글명  (예: 업무 분류)
    mClassVar    : 마스터 VO 변수명    (예: masterVO)
    mMappingUrl  : 마스터 URL 접두어   (예: /master)
    mPkName      : 마스터 PK 필드명   (예: masterId)
    mFormFields  : 마스터 폼 필드
                   [{name, label, type(text|textarea|select|radio|number),
                     required(Y/N), options?:[{value,label}]}]
    isUpdate     : 수정 모드 여부 (true/false)
  =====================================================================
-->
<#assign TH_S = r'${'>
<#assign TH_E = r'}'>
<#assign pageLabel = isUpdate?then('수정', '등록')>
<head>
    <title>${mClassLabel} ${pageLabel}</title>
    <link rel="stylesheet" th:href="@{/css/ftc-portal.css}">
    <style>
        * { box-sizing: border-box; }
        a { text-decoration: none; color: inherit; }
        input:focus, select:focus, textarea:focus { outline: 2px solid #256ef4; outline-offset: -1px; }
        input::placeholder, textarea::placeholder { color: #b0b8c1; }
        .ftc-err { border-color: #d9363e !important; }
        .ftc-err-msg { display:flex;align-items:center;gap:4px;font-size:12px;color:#d9363e;margin-top:5px; }
    </style>
</head>
<body>
<th:block layout:fragment="content">

<div style="font-family:'Pretendard GOV',-apple-system,sans-serif;color:#1e2124;background:#fff;line-height:1.5;">
  <div style="max-width:1200px;margin:0 auto;padding:32px 24px 60px;display:flex;gap:32px;align-items:flex-start;">

    <!-- ① LNB -->
    <aside th:replace="~{layout/lnb :: lnb}"></aside>

    <!-- ② CONTENT -->
    <div style="flex:1;min-width:0;">

      <!-- 브레드크럼 -->
      <nav style="display:flex;align-items:center;gap:6px;font-size:13px;color:#8a949e;margin-bottom:20px;flex-wrap:nowrap;white-space:nowrap;"
           th:replace="~{layout/breadcrumb :: breadcrumb}"></nav>

      <!-- 페이지 제목 -->
      <div style="padding-bottom:16px;border-bottom:2px solid #1e2124;margin-bottom:24px;">
        <h1 style="font-size:26px;font-weight:800;color:#1e2124;margin:0;">${mClassLabel} ${pageLabel}</h1>
      </div>

      <!-- 필수항목 안내 -->
      <div style="display:flex;align-items:center;gap:4px;font-size:13px;color:#6d7882;margin-bottom:12px;justify-content:flex-end;">
        <span style="color:#d9363e;font-weight:700;font-size:15px;">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
      </div>

      <!-- 입력 폼 (krds-table-wrap tbl col) -->
      <form th:action="@{${mMappingUrl}/<#if isUpdate>update<#else>insert</#if>}"
            th:object="${TH_S}${mClassVar}${TH_E}"
            method="post">
        <input type="hidden" name="_csrf" th:value="${TH_S}_csrf.token${TH_E}">
<#if isUpdate>
        <input type="hidden" th:field="*{${mPkName}}">
</#if>

        <table style="width:100%;border-collapse:collapse;font-size:14px;border-top:2px solid #1e2124;">
          <colgroup>
            <col style="width:140px">
            <col>
            <col style="width:140px">
            <col>
          </colgroup>
          <tbody>
<#-- 2컬럼 행 배치 -->
<#list mFormFields as field>
  <#if field?is_odd_item>
            <tr style="border-bottom:1px solid #e8eaec;">
              <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;vertical-align:top;white-space:nowrap;">
                ${field.label}<#if field.required == 'Y'><span style="color:#d9363e;margin-left:2px;">*</span></#if>
              </th>
              <td style="padding:12px 16px;vertical-align:top;<#if !field?has_next>colspan="3"</#if>">
    <#if field.type == 'select'>
                <select th:field="*{${field.name}}"
                        th:errorclass="ftc-err"
                        style="width:100%;padding:9px 32px 9px 12px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;appearance:none;background:#fff url('data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2210%22 height=%226%22 viewBox=%220 0 10 6%22><path d=%22M1 1l4 4 4-4%22 stroke=%22%238a949e%22 stroke-width=%221.5%22 fill=%22none%22/></svg>') no-repeat right 10px center;">
                  <option value="">선택하세요</option>
      <#list field.options as opt>
                  <option value="${opt.value}">${opt.label}</option>
      </#list>
                </select>
    <#elseif field.type == 'textarea'>
                <textarea th:field="*{${field.name}}"
                          th:errorclass="ftc-err"
                          rows="5"
                          placeholder="${field.label}을(를) 입력하세요"
                          style="width:100%;padding:12px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;resize:vertical;line-height:1.7;"></textarea>
    <#elseif field.type == 'radio'>
                <div style="display:flex;gap:20px;align-items:center;padding:6px 0;">
      <#list field.options as opt>
                  <label style="display:flex;align-items:center;gap:6px;cursor:pointer;font-size:14px;">
                    <input type="radio" th:field="*{${field.name}}" value="${opt.value}"
                           style="width:16px;height:16px;accent-color:#256ef4;cursor:pointer;">
                    ${opt.label}
                  </label>
      </#list>
                </div>
    <#elseif field.type == 'number'>
                <input type="number" th:field="*{${field.name}}"
                       th:errorclass="ftc-err"
                       min="0"
                       style="width:120px;padding:9px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;text-align:right;">
    <#elseif field.type == 'date'>
                <input type="date" th:field="*{${field.name}}"
                       th:errorclass="ftc-err"
                       style="width:180px;padding:9px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;">
    <#else>
                <input type="text" th:field="*{${field.name}}"
                       th:errorclass="ftc-err"
                       placeholder="${field.label}을(를) 입력하세요"
                       style="width:100%;padding:9px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;">
    </#if>
    <#if field.required == 'Y'>
                <div th:if="${TH_S}#fields.hasErrors('${field.name}')${TH_E}" class="ftc-err-msg">
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="6.5" cy="6.5" r="5.5" stroke="#d9363e" stroke-width="1.3"/><line x1="6.5" y1="4" x2="6.5" y2="7" stroke="#d9363e" stroke-width="1.3" stroke-linecap="round"/><circle cx="6.5" cy="9.5" r="0.7" fill="#d9363e"/></svg>
                  <span th:errors="*{${field.name}}"></span>
                </div>
    </#if>
              </td>
    <#if field?has_next>
      <#assign nf = mFormFields[field?index + 1]>
              <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;vertical-align:top;white-space:nowrap;">
                ${nf.label}<#if nf.required == 'Y'><span style="color:#d9363e;margin-left:2px;">*</span></#if>
              </th>
              <td style="padding:12px 16px;vertical-align:top;">
      <#if nf.type == 'select'>
                <select th:field="*{${nf.name}}"
                        th:errorclass="ftc-err"
                        style="width:100%;padding:9px 32px 9px 12px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;appearance:none;background:#fff url('data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2210%22 height=%226%22 viewBox=%220 0 10 6%22><path d=%22M1 1l4 4 4-4%22 stroke=%22%238a949e%22 stroke-width=%221.5%22 fill=%22none%22/></svg>') no-repeat right 10px center;">
                  <option value="">선택하세요</option>
        <#list nf.options as opt>
                  <option value="${opt.value}">${opt.label}</option>
        </#list>
                </select>
      <#elseif nf.type == 'radio'>
                <div style="display:flex;gap:20px;align-items:center;padding:6px 0;">
        <#list nf.options as opt>
                  <label style="display:flex;align-items:center;gap:6px;cursor:pointer;font-size:14px;">
                    <input type="radio" th:field="*{${nf.name}}" value="${opt.value}"
                           style="width:16px;height:16px;accent-color:#256ef4;cursor:pointer;">
                    ${opt.label}
                  </label>
        </#list>
                </div>
      <#elseif nf.type == 'textarea'>
                <textarea th:field="*{${nf.name}}"
                          th:errorclass="ftc-err"
                          rows="5"
                          style="width:100%;padding:12px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;resize:vertical;line-height:1.7;"></textarea>
      <#elseif nf.type == 'number'>
                <input type="number" th:field="*{${nf.name}}"
                       th:errorclass="ftc-err"
                       min="0"
                       style="width:120px;padding:9px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;text-align:right;">
      <#else>
                <input type="text" th:field="*{${nf.name}}"
                       th:errorclass="ftc-err"
                       placeholder="${nf.label}을(를) 입력하세요"
                       style="width:100%;padding:9px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;">
      </#if>
      <#if nf.required == 'Y'>
                <div th:if="${TH_S}#fields.hasErrors('${nf.name}')${TH_E}" class="ftc-err-msg">
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="6.5" cy="6.5" r="5.5" stroke="#d9363e" stroke-width="1.3"/><line x1="6.5" y1="4" x2="6.5" y2="7" stroke="#d9363e" stroke-width="1.3" stroke-linecap="round"/><circle cx="6.5" cy="9.5" r="0.7" fill="#d9363e"/></svg>
                  <span th:errors="*{${nf.name}}"></span>
                </div>
      </#if>
              </td>
    </#if>
            </tr>
  </#if>
</#list>
          </tbody>
        </table>

        <!-- 전체 유효성 오류 배너 -->
        <div th:if="${TH_S}#fields.hasErrors('*')${TH_E}"
             style="display:flex;align-items:center;gap:10px;margin-top:16px;padding:14px 18px;background:#fff6f6;border:1px solid #f5c0c2;border-radius:6px;font-size:14px;color:#d9363e;">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><circle cx="9" cy="9" r="8" stroke="#d9363e" stroke-width="1.5"/><line x1="9" y1="5.5" x2="9" y2="10" stroke="#d9363e" stroke-width="1.5"/><circle cx="9" cy="12.5" r="1" fill="#d9363e"/></svg>
          필수 입력 항목을 모두 입력해 주세요.
        </div>

        <!-- 하단 버튼 (krds-btn) -->
        <div style="display:flex;justify-content:center;gap:10px;margin-top:32px;">
          <a th:href="@{${mMappingUrl}/list}"
             style="display:inline-flex;align-items:center;gap:6px;padding:11px 40px;border:1px solid #cdd1d5;border-radius:4px;font-size:15px;font-weight:600;color:#464c53;background:#fff;">
            취소
          </a>
          <button type="submit"
                  style="display:inline-flex;align-items:center;gap:6px;padding:11px 40px;border:none;border-radius:4px;font-size:15px;font-weight:700;color:#fff;background:#083891;cursor:pointer;font-family:inherit;">
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none" stroke="#fff" stroke-width="1.8"><path d="M2 8l4 4 7-7"/></svg>
            저장
          </button>
        </div>

      </form>
    </div><!-- /CONTENT -->
  </div><!-- /wrapper -->
</div>

</th:block>
</body>
</html>
