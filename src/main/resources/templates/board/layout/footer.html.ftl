<footer th:fragment="footer" style="background:#f4f5f6;border-top:1px solid #e8eaec;">
    <div style="max-width:1200px;margin:0 auto;padding:28px 24px 24px;font-size:13px;color:#464c53;">
        <div style="display:flex;align-items:center;gap:10px;font-size:16px;font-weight:800;color:#083891;margin-bottom:12px;">
            <span aria-hidden="true"
                  style="width:28px;height:28px;border-radius:50%;background:#083891;display:inline-flex;align-items:center;justify-content:center;color:#fff;font-size:12px;">e</span>
            <span>기관 누리집</span>
        </div>
        <p style="margin:0 0 4px;">(30108) 세종특별자치시 예시 주소</p>
        <p style="margin:0;"><strong>정부민원안내콜센터 국번없이 110</strong> (무료)</p>
    </div>
    <div style="border-top:1px solid #e0e2e4;">
        <div style="max-width:1200px;height:48px;margin:0 auto;padding:0 24px;display:flex;align-items:center;gap:20px;font-size:13px;">
            <a th:href="@{/}" style="color:#256ef4;font-weight:700;">개인정보처리방침</a>
            <a th:href="@{/}">저작권 정책</a>
            <span style="margin-left:auto;color:#8a949e;">© eGovFrame. All rights reserved.</span>
        </div>
    </div>
</footer>
