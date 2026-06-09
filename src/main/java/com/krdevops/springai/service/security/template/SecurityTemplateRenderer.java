package com.krdevops.springai.service.security.template;

import com.krdevops.springai.model.SecuritySpec;
import com.krdevops.springai.model.VersionCapability;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * templates/security/ 경로의 .tpl 파일을 로드하여 변수 치환 후 반환한다.
 *
 * <p>치환 변수:
 * <ul>
 *   <li>{@code ${packageName}}   — Java 패키지명</li>
 *   <li>{@code ${packagePath}}   — 패키지 경로 (. → /)</li>
 *   <li>{@code ${basePackage}}   — 패키지 마지막 세그먼트</li>
 *   <li>{@code ${egovVersion}}   — "4.3" | "5.0"</li>
 *   <li>{@code ${javaxOrJakarta}} — "javax" | "jakarta"</li>
 * </ul>
 *
 * <p>맵에 없는 키(Spring EL, JSP EL 등)는 원본 {@code ${key}} 형태로 그대로 유지된다.
 */
@Component
public class SecurityTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final String  BASE_PATH   = "templates/security/";

    /**
     * securityType에 해당하는 .tpl 파일을 로드하여 spec 기반 변수를 치환한 결과를 반환한다.
     *
     * @throws IllegalArgumentException 지원하지 않는 securityType인 경우
     * @throws IllegalStateException    템플릿 파일을 읽을 수 없는 경우
     */
    public String render(String type, SecuritySpec spec) {
        String name = templateName(type, spec.capability());
        Map<String, String> vars = buildVars(spec);
        return load(name, vars);
    }

    /**
     * securityType과 VersionCapability를 보고 classpath 상의 상대 경로를 결정한다.
     * successHandler/failureHandler는 ${javaxOrJakarta} 변수로 버전 분기를 처리하므로
     * egov43/ 템플릿 하나만 사용한다.
     */
    String templateName(String type, VersionCapability cap) {
        boolean is43 = !cap.jakarta();
        return switch (type.toLowerCase()) {
            // ⚠️ 4.3 WAR XML Security 전용 — 5.0에서는 egovSpringSecurityLoginFilter Bean이 없으므로 미지원
            //    단일 호출(renderSingle)과 파일 저장(plan) 모두 이 경로를 타므로 Renderer에서 한 번만 막는다.
            case "webxmlfilter" -> {
                if (!is43) {
                    throw new IllegalArgumentException(
                            "webXmlFilter는 eGovFrame 4.3 WAR XML Security 전용입니다. " +
                            "5.0에서는 setup-war-50을 사용하세요.");
                }
                yield "common/web-xml-filter.tpl";
            }
            case "contextsecurity"      -> is43 ? "egov43/context-security.xml.tpl"
                                                : "egov50/context-security.xml.tpl";
            case "securitymapper"       -> "common/security-mapper.sql.tpl";
            case "javaconfig"           -> is43 ? "egov43/java-config.java.tpl"
                                                : "egov50/java-config.java.tpl";
            case "userdetailsservice"   -> is43 ? "egov43/user-details-service.java.tpl"
                                                : "egov50/user-details-service-notice.tpl";
            case "rolehierarchy"        -> is43 ? "egov43/role-hierarchy.java.tpl"
                                                : "egov50/role-hierarchy.java.tpl";
            case "loginpage"            -> "common/login-page.jsp.tpl";
            case "successhandler"       -> "egov43/success-handler.java.tpl";   // ${javaxOrJakarta} 로 버전 분기
            case "failurehandler"       -> "egov43/failure-handler.java.tpl";   // ${javaxOrJakarta} 로 버전 분기
            case "accessdeniedhandler"  -> is43 ? "egov43/access-denied-handler.java.tpl"
                                                : "egov50/access-denied-handler.java.tpl";
            case "loginfilter"          -> "common/login-filter.java.tpl";
            case "logoutfilter"         -> "common/logout-filter.java.tpl";
            case "loginpolicyfilter"    -> "common/login-policy-filter.java.tpl";
            case "sessionmapping"       -> "common/session-mapping.java.tpl";
            case "userdetailshelper"    -> "common/user-details-helper.java.tpl";
            case "userdetailshelperxml" -> "common/user-details-helper-xml.tpl";
            default -> throw new IllegalArgumentException("지원하지 않는 securityType: " + type);
        };
    }

    Map<String, String> buildVars(SecuritySpec spec) {
        return Map.of(
                "packageName",    spec.packageName(),
                "packagePath",    spec.packagePath(),
                "basePackage",    spec.basePackage(),
                "egovVersion",    spec.capability().egovVersion(),
                "javaxOrJakarta", spec.javaxOrJakarta()
        );
    }

    private String load(String name, Map<String, String> vars) {
        String template = readTemplate(name);
        return substitute(template, vars);
    }

    private String readTemplate(String name) {
        try {
            ClassPathResource resource = new ClassPathResource(BASE_PATH + name);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "보안 템플릿 파일을 읽을 수 없습니다: " + BASE_PATH + name, e);
        }
    }

    private String substitute(String template, Map<String, String> vars) {
        Matcher m  = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key   = m.group(1);
            String value = vars.get(key);
            m.appendReplacement(sb, value != null
                    ? Matcher.quoteReplacement(value)
                    : Matcher.quoteReplacement(m.group(0))); // 미매칭 키는 원본 유지
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
