<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<#--
  =====================================================================
  eGovFrame SpringAI MCP CrudPromptBuilderTool
  Template : masterdetail/thymeleaf-detail.html.ftl
  Design   : FTC 스타일 (KRDS) — CRUD 마스터-디테일 상세형 (1:N)
  =====================================================================
  FreeMarker 변수 (Master)
    mClassLabel  : 마스터 화면 한글명  (예: 업무 분류)
    mClassVar    : 마스터 VO 변수명    (예: masterVO)
    mMappingUrl  : 마스터 URL 접두어   (예: /master)
    mPkName      : 마스터 PK 필드명   (예: masterId)
    mDetailFields: 마스터 상세 필드    [{name, label}]
  FreeMarker 변수 (Detail)
    dClassLabel  : 디테일 화면 한글명  (예: 하위 항목)
    dClassVar    : 디테일 VO 변수명    (예: detailVO)
    dMappingUrl  : 디테일 URL 접두어   (예: /detail)
    dPkName      : 디테일 PK 필드명   (예: detailId)
    dListFields  : 디테일 목록 필드    [{name, label, width?}]
  =====================================================================
-->
<#assign TH_S = r'${'>
<#assign TH_E = r'}'>
<head>
    <title>${mClassLabel} 상세</title>
    <link rel="stylesheet" th:href="@{/css/ftc-portal.css}">
    <style>
        * { box-sizing: border-box; }
        a { text-decoration: none; color: inherit; }
        .crud-tr:hover td { background: #f0f5ff !important; }
        .dt-edit-btn { display:inline-flex;align-items:center;gap:3px;padding:4px 10px;border:1px solid #b8d0f8;border-radius:3px;font-size:12px;font-weight:600;color:#256ef4;background:#fff;cursor:pointer;font-family:inherit; }
        .dt-del-btn  { display:inline-flex;align-items:center;gap:3px;padding:4px 10px;border:1px solid #f5c0c2;border-radius:3px;font-size:12px;font-weight:600;color:#d9363e;background:#fff;cursor:pointer;font-family:inherit; }
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

      <!-- 페이지 제목 + 목록 버튼 -->
      <div style="display:flex;align-items:center;justify-content:space-between;padding-bottom:16px;border-bottom:2px solid #1e2124;margin-bottom:24px;">
        <h1 style="font-size:26px;font-weight:800;color:#1e2124;margin:0;">${mClassLabel} 상세</h1>
        <a th:href="@{${mMappingUrl}/list}"
           style="display:inline-flex;align-items:center;gap:5px;padding:9px 20px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-weight:600;color:#464c53;">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="#464c53" stroke-width="1.5"><line x1="1" y1="4" x2="12" y2="4"/><line x1="1" y1="7" x2="12" y2="7"/><line x1="1" y1="10" x2="12" y2="10"/></svg>
          목록
        </a>
      </div>

      <!-- ① 마스터 정보 테이블 (krds-table-wrap tbl col) -->
      <div style="margin-bottom:32px;">
        <div style="display:flex;align-items:center;margin-bottom:12px;">
          <h2 style="font-size:16px;font-weight:800;color:#083891;margin:0;display:flex;align-items:center;gap:7px;">
            <span style="width:4px;height:18px;background:#256ef4;border-radius:2px;display:inline-block;flex:none;"></span>
            ${mClassLabel} 정보
          </h2>
        </div>
        <table style="width:100%;border-collapse:collapse;font-size:14px;border-top:2px solid #1e2124;">
          <colgroup><col style="width:140px"><col><col style="width:140px"><col></colgroup>
          <tbody>
            <!-- PK + 상태 -->
            <tr style="border-bottom:1px solid #e8eaec;">
              <th scope="row" style="padding:13px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;white-space:nowrap;">코드</th>
              <td th:text="${TH_S}${mClassVar}.${mPkName}${TH_E}"
                  style="padding:13px 16px;font-family:monospace;font-size:15px;font-weight:700;color:#256ef4;letter-spacing:0.04em;"></td>
              <th scope="row" style="padding:13px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;white-space:nowrap;">상태</th>
              <td style="padding:13px 16px;">
                <span th:if="${TH_S}'Y'.equals(${mClassVar}.useAt)${TH_E}"
                      style="display:inline-block;padding:3px 12px;background:#e8f5ee;border-radius:20px;font-size:12px;font-weight:700;color:#1a7f42;">사용</span>
                <span th:unless="${TH_S}'Y'.equals(${mClassVar}.useAt)${TH_E}"
                      style="display:inline-block;padding:3px 12px;background:#f6f7f8;border-radius:20px;font-size:12px;font-weight:700;color:#8a949e;">중지</span>
              </td>
            </tr>
<#-- 마스터 상세 필드 2컬럼 배치 -->
<#list mDetailFields as field>
  <#if field?is_odd_item>
            <tr style="border-bottom:1px solid #e8eaec;">
              <th scope="row" style="padding:13px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;white-space:nowrap;">${field.label}</th>
              <td th:text="${TH_S}${mClassVar}.${field.name}${TH_E}"
                  style="padding:13px 16px;color:#464c53;"><#if !field?has_next> colspan="3"</#if></td>
    <#if field?has_next>
      <#assign nf = mDetailFields[field?index + 1]>
              <th scope="row" style="padding:13px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;white-space:nowrap;">${nf.label}</th>
              <td th:text="${TH_S}${mClassVar}.${nf.name}${TH_E}"
                  style="padding:13px 16px;color:#464c53;"></td>
    <#else>
              <th scope="row" style="padding:13px 16px;background:#f6f7f8;"></th>
              <td style="padding:13px 16px;"></td>
    </#if>
            </tr>
  </#if>
</#list>
            <!-- 등록일/수정일 -->
            <tr style="border-bottom:1px solid #e8eaec;">
              <th scope="row" style="padding:13px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;">등록일</th>
              <td th:text="${TH_S}${mClassVar}.regDt${TH_E}" style="padding:13px 16px;color:#464c53;"></td>
              <th scope="row" style="padding:13px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;">수정일</th>
              <td th:text="${TH_S}${mClassVar}.updtDt${TH_E}" style="padding:13px 16px;color:#464c53;"></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ② 디테일 목록 (1:N) -->
      <div>
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">
          <h2 style="font-size:16px;font-weight:800;color:#083891;margin:0;display:flex;align-items:center;gap:7px;">
            <span style="width:4px;height:18px;background:#256ef4;border-radius:2px;display:inline-block;flex:none;"></span>
            ${dClassLabel} 목록
            <span th:text="'총 ' + ${TH_S}${dClassVar}List.size()${TH_E} + '건'"
                  style="font-size:13px;font-weight:600;color:#8a949e;margin-left:4px;"></span>
          </h2>
          <a th:href="@{${dMappingUrl}/regist(${mPkName}=${TH_S}${mClassVar}.${mPkName}${TH_E})}"
             style="display:inline-flex;align-items:center;gap:5px;padding:7px 16px;background:#083891;border-radius:4px;font-size:13px;font-weight:700;color:#fff;">
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="#fff" stroke-width="2"><line x1="6" y1="1" x2="6" y2="11"/><line x1="1" y1="6" x2="11" y2="6"/></svg>
            ${dClassLabel} 등록
          </a>
        </div>

        <div style="border:1px solid #e8eaec;border-radius:4px;overflow:hidden;">
          <table style="width:100%;border-collapse:collapse;font-size:14px;">
            <thead>
              <tr style="background:#f6f7f8;border-top:2px solid #1e2124;">
                <th style="padding:11px 8px;text-align:center;font-weight:700;color:#464c53;width:54px;border-bottom:1px solid #e8eaec;">번호</th>
<#list dListFields as field>
                <th style="padding:11px 12px;text-align:left;font-weight:700;color:#464c53;<#if field.width??>width:${field.width};</#if>border-bottom:1px solid #e8eaec;">${field.label}</th>
</#list>
                <th style="padding:11px 8px;text-align:center;font-weight:700;color:#464c53;width:70px;border-bottom:1px solid #e8eaec;">상태</th>
                <th style="padding:11px 8px;text-align:center;font-weight:700;color:#464c53;width:100px;border-bottom:1px solid #e8eaec;">등록일</th>
                <th style="padding:11px 8px;text-align:center;font-weight:700;color:#464c53;width:110px;border-bottom:1px solid #e8eaec;">관리</th>
              </tr>
            </thead>
            <tbody>
              <tr th:each="${dClassVar}, iterStat : ${TH_S}${dClassVar}List${TH_E}"
                  class="crud-tr"
                  style="border-bottom:1px solid #f0f1f3;cursor:pointer;"
                  th:onclick="|location.href='@{${dMappingUrl}/detail(${dPkName}=${TH_S}${dClassVar}.${dPkName}${TH_E})}'|">
                <td th:text="${TH_S}${dClassVar}List.size() - iterStat.index${TH_E}"
                    style="padding:11px 8px;text-align:center;color:#8a949e;font-size:13px;"></td>
<#list dListFields as field>
  <#if field?is_first>
                <td style="padding:11px 12px;">
                  <a th:href="@{${dMappingUrl}/detail(${dPkName}=${TH_S}${dClassVar}.${dPkName}${TH_E})}"
                     th:text="${TH_S}${dClassVar}.${field.name}${TH_E}"
                     style="font-size:14px;color:#1e2124;" onclick="event.stopPropagation()"></a>
                </td>
  <#else>
                <td th:text="${TH_S}${dClassVar}.${field.name}${TH_E}"
                    style="padding:11px 12px;color:#464c53;"></td>
  </#if>
</#list>
                <td style="padding:11px 8px;text-align:center;">
                  <span th:if="${TH_S}'Y'.equals(${dClassVar}.useAt)${TH_E}"
                        style="display:inline-block;padding:2px 8px;background:#e8f5ee;border-radius:20px;font-size:11px;font-weight:700;color:#1a7f42;">사용</span>
                  <span th:unless="${TH_S}'Y'.equals(${dClassVar}.useAt)${TH_E}"
                        style="display:inline-block;padding:2px 8px;background:#f6f7f8;border-radius:20px;font-size:11px;font-weight:700;color:#8a949e;">중지</span>
                </td>
                <td th:text="${TH_S}${dClassVar}.regDt${TH_E}"
                    style="padding:11px 8px;text-align:center;color:#8a949e;font-size:12px;"></td>
                <td style="padding:11px 8px;text-align:center;" onclick="event.stopPropagation()">
                  <div style="display:flex;justify-content:center;gap:4px;">
                    <a th:href="@{${dMappingUrl}/updateView(${dPkName}=${TH_S}${dClassVar}.${dPkName}${TH_E})}"
                       class="dt-edit-btn">
                      <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="#256ef4" stroke-width="1.4"><path d="M6.5 1l2.5 2.5-5.5 5.5H1V6.5l5.5-5.5z"/></svg>수정
                    </a>
                    <form th:action="@{${dMappingUrl}/delete}" method="post" style="display:inline;"
                          onsubmit="event.stopPropagation(); return confirm('삭제하시겠습니까?');">
                      <input type="hidden" name="${dPkName}" th:value="${TH_S}${dClassVar}.${dPkName}${TH_E}">
                      <input type="hidden" name="${mPkName}" th:value="${TH_S}${mClassVar}.${mPkName}${TH_E}">
                      <input type="hidden" name="_csrf" th:value="${TH_S}_csrf.token${TH_E}">
                      <button type="submit" class="dt-del-btn">
                        <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="#d9363e" stroke-width="1.4"><polyline points="1,2.5 9,2.5"/><path d="M3.5 2.5V1.5h3v1"/><rect x="1.5" y="2.5" width="7" height="6.5" rx="1"/></svg>삭제
                      </button>
                    </form>
                  </div>
                </td>
              </tr>
              <!-- 디테일 없음 -->
              <tr th:if="${TH_S}${dClassVar}List.empty${TH_E}">
                <td colspan="${dListFields?size + 4}"
                    style="padding:40px 0;text-align:center;color:#8a949e;font-size:14px;">
                  <div style="display:flex;flex-direction:column;align-items:center;gap:8px;">
                    <svg width="32" height="32" viewBox="0 0 32 32" fill="none"><rect x="4" y="4" width="24" height="24" rx="3" stroke="#cdd1d5" stroke-width="1.6"/><line x1="9" y1="12" x2="23" y2="12" stroke="#cdd1d5" stroke-width="1.4"/><line x1="9" y1="16" x2="23" y2="16" stroke="#cdd1d5" stroke-width="1.4"/></svg>
                    등록된 ${dClassLabel} 항목이 없습니다.
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 하단 버튼 -->
      <div style="display:flex;justify-content:space-between;align-items:center;margin-top:32px;">
        <a th:href="@{${mMappingUrl}/list}"
           style="display:inline-flex;align-items:center;gap:6px;padding:10px 28px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-weight:600;color:#464c53;">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="#464c53" stroke-width="1.5"><line x1="1" y1="4" x2="12" y2="4"/><line x1="1" y1="7" x2="12" y2="7"/><line x1="1" y1="10" x2="12" y2="10"/></svg>
          목록
        </a>
        <div style="display:flex;gap:8px;">
          <a th:href="@{${mMappingUrl}/updateView(${mPkName}=${TH_S}${mClassVar}.${mPkName}${TH_E})}"
             style="display:inline-flex;align-items:center;gap:5px;padding:10px 28px;border:1px solid #256ef4;border-radius:4px;font-size:14px;font-weight:600;color:#256ef4;">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="#256ef4" stroke-width="1.5"><path d="M9 1.5l2.5 2.5-7 7H2v-2.5l7-7z"/></svg>
            수정
          </a>
          <form th:action="@{${mMappingUrl}/delete}" method="post" style="display:inline;"
                onsubmit="return confirm('마스터를 삭제하면 모든 하위 항목도 삭제됩니다. 계속하시겠습니까?');">
            <input type="hidden" name="${mPkName}" th:value="${TH_S}${mClassVar}.${mPkName}${TH_E}">
            <input type="hidden" name="_csrf" th:value="${TH_S}_csrf.token${TH_E}">
            <button type="submit"
                    style="display:inline-flex;align-items:center;gap:5px;padding:10px 28px;border:1px solid #d9363e;border-radius:4px;font-size:14px;font-weight:600;color:#d9363e;background:#fff;cursor:pointer;font-family:inherit;">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="#d9363e" stroke-width="1.5"><polyline points="2,3.5 11,3.5"/><path d="M4.5 3.5V2h4v1.5"/><rect x="2.5" y="3.5" width="8" height="8" rx="1"/></svg>
              삭제
            </button>
          </form>
        </div>
      </div>

    </div><!-- /CONTENT -->
  </div><!-- /wrapper -->
</div>

</th:block>
</body>
</html>
