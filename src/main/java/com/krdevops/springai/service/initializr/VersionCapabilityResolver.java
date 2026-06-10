package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.VersionCapability;
import org.springframework.stereotype.Component;

@Component
public class VersionCapabilityResolver {

    // ── Capability별 독립 임계값 ──
    private static final String JAKARTA_SINCE    = "5.0";
    private static final String SPRING6_SINCE    = "5.0";
    private static final String BOOT3_SINCE      = "5.0";
    private static final String JAVA17_SINCE     = "5.0";
    private static final String PARENT_SINCE     = "5.0";
    private static final String HYPHEN_ID_SINCE  = "5.0";
    private static final String MYBATIS3_SINCE   = "5.0";

    /** 버전 문자열 룩업 테이블 — is50 삼항 제거, 5.1이 나오면 행 하나만 추가 */
    private record VersionTable(
        String egovVersion, String javaVersion,
        String springVersion, String springBootVersion, String securityVersion
    ) {}

    private static final VersionTable V50 = new VersionTable("5.0", "17", "6.2.11", "3.5.6", "6.5.5");
    private static final VersionTable V43 = new VersionTable("4.3", "11", "5.3.37", "2.7.18", "5.8.13");

    public VersionCapability resolve(String egovVersion) {
        VersionTable t = gte(egovVersion, "5.0") ? V50 : V43;

        return new VersionCapability(
            gte(egovVersion, JAKARTA_SINCE),
            gte(egovVersion, SPRING6_SINCE),
            gte(egovVersion, BOOT3_SINCE),
            gte(egovVersion, JAVA17_SINCE),
            gte(egovVersion, PARENT_SINCE),
            gte(egovVersion, HYPHEN_ID_SINCE),
            gte(egovVersion, MYBATIS3_SINCE),
            t.egovVersion(), t.javaVersion(), t.springVersion(),
            t.springBootVersion(), t.securityVersion()
        );
    }

    /** 시맨틱 버전 비교 — "latest"/"5.0" → 5.0.0 해석 */
    private static boolean gte(String version, String threshold) {
        String v = (version == null || version.isBlank()) ? "5.0.0"
                 : "latest".equalsIgnoreCase(version) ? "5.0.0"
                 : version;
        String[] vp = v.split("\\.");
        String[] tp = threshold.split("\\.");
        int len = Math.max(vp.length, tp.length);
        for (int i = 0; i < len; i++) {
            int vn = i < vp.length ? seg(vp[i]) : 0;
            int tn = i < tp.length ? seg(tp[i]) : 0;
            if (vn != tn) return vn > tn;
        }
        return true;
    }

    private static int seg(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
