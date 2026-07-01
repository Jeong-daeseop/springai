<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<#--
  =====================================================================
  eGovFrame SpringAI MCP CrudPromptBuilderTool
  Template : board/thymeleaf-regist.html.ftl
  Design   : FTC 스타일 (KRDS)
  =====================================================================
  FreeMarker 변수
    classLabel   : 화면 한글명   (예: 공지·공고)
    classVar     : VO 변수명     (예: board)
    ClassName    : VO 클래스명   (예: Board)
    mappingUrl   : URL 접두어   (예: /board)
    pkName       : PK 필드명    (예: boardNo)
    formFields   : 폼 입력 필드 리스트
                   [{name, label, type(text|textarea|select|radio|date|number),
                     required(Y/N), options?:[{value,label}], colspan?}]
    hasFileYn    : 첨부파일 여부  (Y/N)
    isUpdate     : 수정 모드 여부 (true/false) — 수정이면 PUT
  =====================================================================
-->
<#assign TH_S = r'${'>
<#assign TH_E = r'}'>
<#assign pageLabel = isUpdate?then('수정', '등록')>
<head>
    <title>${classLabel} ${pageLabel}</title>
    <link rel="stylesheet" th:href="@{/css/ftc-portal.css}">
    <style>
        * { box-sizing: border-box; }
        a { text-decoration: none; color: inherit; }
        input:focus, select:focus, textarea:focus { outline: 2px solid #256ef4; outline-offset: -1px; }
        input::placeholder, textarea::placeholder { color: #b0b8c1; }
        .ftc-err-msg { display:flex;align-items:center;gap:4px;font-size:12px;color:#d9363e;margin-top:5px; }
        .ftc-err-msg span { display:inline-block;width:13px;height:13px;border:1.3px solid #d9363e;border-radius:50%;text-align:center;font-size:10px;font-weight:700;line-height:12px;flex:none; }
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
      <nav style="display:flex;align-items:center;gap:6px;font-size:13px;color:#8a949e;margin-bottom:20px;" th:replace="~{layout/breadcrumb :: breadcrumb}"></nav>

      <!-- 페이지 제목 -->
      <div style="padding-bottom:16px;border-bottom:2px solid #1e2124;margin-bottom:24px;">
        <h1 style="font-size:26px;font-weight:800;color:#1e2124;margin:0;">${classLabel} ${pageLabel}</h1>
      </div>

      <!-- 필수항목 안내 -->
      <div style="display:flex;align-items:center;gap:4px;font-size:13px;color:#6d7882;margin-bottom:12px;justify-content:flex-end;">
        <span style="color:#d9363e;font-weight:700;font-size:15px;">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
      </div>

      <!-- 입력 폼 -->
      <form th:action="@{${mappingUrl}/<#if isUpdate>update<#else>insert</#if>}"
            th:object="${TH_S}${classVar}VO${TH_E}"
            method="post"
            enctype="multipart/form-data">
        <input type="hidden" name="_csrf" th:value="${TH_S}_csrf.token${TH_E}">
<#if isUpdate>
        <input type="hidden" th:field="*{${pkName}}">
</#if>

        <table style="width:100%;border-collapse:collapse;font-size:14px;border-top:2px solid #1e2124;">
          <colgroup>
            <col style="width:140px">
            <col>
            <col style="width:140px">
            <col>
          </colgroup>
          <tbody>
<#-- 홀수/짝수 필드를 2컬럼 행으로 배치 -->
<#list formFields as field>
  <#if field?is_odd_item>
          <tr style="border-bottom:1px solid #e8eaec;">
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;vertical-align:top;white-space:nowrap;">
              ${field.label}<#if field.required == 'Y'><span style="color:#d9363e;margin-left:2px;">*</span></#if>
            </th>
            <td style="padding:12px 16px;vertical-align:top;<#if !field?has_next>colspan="3"</#if>">
              <#include "field-input.ftl">
            </td>
    <#if field?has_next>
      <#assign nf = formFields[field?index + 1]>
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;vertical-align:top;white-space:nowrap;">
              ${nf.label}<#if nf.required == 'Y'><span style="color:#d9363e;margin-left:2px;">*</span></#if>
            </th>
            <td style="padding:12px 16px;vertical-align:top;">
              <#assign field = nf>
              <#include "field-input.ftl">
            </td>
    </#if>
          </tr>
  </#if>
</#list>
<#if hasFileYn == 'Y'>
          <!-- 첨부파일 -->
          <tr style="border-bottom:1px solid #e8eaec;">
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;vertical-align:top;">첨부파일</th>
            <td colspan="3" style="padding:14px 16px;">
              <!-- 드래그 업로드 영역 -->
              <div style="border:2px dashed #cdd1d5;border-radius:6px;padding:20px;text-align:center;background:#fafbfc;">
                <label style="display:inline-flex;align-items:center;gap:6px;padding:7px 18px;background:#fff;border:1px solid #256ef4;border-radius:4px;font-size:13px;font-weight:600;color:#256ef4;cursor:pointer;">
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="#256ef4" stroke-width="1.5"><path d="M6.5 1v8M3.5 4l3-3 3 3"/><line x1="1" y1="12" x2="12" y2="12"/></svg>
                  파일 선택
                  <input type="file" name="file" multiple style="display:none;" id="fileInput">
                </label>
                <p style="margin:8px 0 0;font-size:12px;color:#b0b8c1;">최대 10MB · PDF, HWP, DOCX, XLSX, ZIP 허용</p>
              </div>
<#if isUpdate>
              <!-- 기존 파일 목록 -->
              <ul id="existingFiles" style="margin-top:12px;display:flex;flex-direction:column;gap:6px;">
                <li th:each="file : ${TH_S}fileList${TH_E}"
                    style="display:flex;align-items:center;gap:8px;padding:8px 12px;background:#f6f7f8;border-radius:4px;font-size:13px;color:#464c53;">
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="#8a949e" stroke-width="1.4"><rect x="2" y="1" width="10" height="12" rx="1.2"/><line x1="4.5" y1="5" x2="9.5" y2="5"/><line x1="4.5" y1="7.5" x2="9.5" y2="7.5"/></svg>
                  <span th:text="${TH_S}file.orignlFileNm${TH_E}" style="flex:1;"></span>
                  <input type="checkbox" name="deleteFileIds" th:value="${TH_S}file.atchFileId + '_' + file.fileSn${TH_E}">
                  <label style="font-size:12px;color:#d9363e;">삭제</label>
                </li>
              </ul>
</#if>
              <!-- 신규 파일 미리보기 목록 -->
              <ul id="newFileList" style="margin-top:8px;display:flex;flex-direction:column;gap:6px;"></ul>
              <script>
                document.getElementById('fileInput').addEventListener('change', function() {
                  const list = document.getElementById('newFileList');
                  list.innerHTML = '';
                  Array.from(this.files).forEach(f => {
                    const li = document.createElement('li');
                    li.style.cssText = 'display:flex;align-items:center;gap:8px;padding:8px 12px;background:#f6f7f8;border-radius:4px;font-size:13px;color:#464c53;';
                    li.textContent = f.name + ' (' + (f.size / 1024).toFixed(1) + 'KB)';
                    list.appendChild(li);
                  });
                });
              </script>
            </td>
          </tr>
</#if>
          </tbody>
        </table>

        <!-- 전체 오류 배너 -->
        <div th:if="${TH_S}#fields.hasErrors('*')${TH_E}"
             style="display:flex;align-items:center;gap:10px;margin-top:16px;padding:14px 18px;background:#fff6f6;border:1px solid #f5c0c2;border-radius:6px;font-size:14px;color:#d9363e;">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><circle cx="9" cy="9" r="8" stroke="#d9363e" stroke-width="1.5"/><line x1="9" y1="5.5" x2="9" y2="10" stroke="#d9363e" stroke-width="1.5"/><circle cx="9" cy="12.5" r="1" fill="#d9363e"/></svg>
          필수 입력 항목을 모두 입력해 주세요.
        </div>

        <!-- 하단 버튼 -->
        <div style="display:flex;justify-content:center;gap:10px;margin-top:32px;">
          <a th:href="@{${mappingUrl}/list}"
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
