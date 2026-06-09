package com.krdevops.springai.service.security;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.SecuritySpec;
import com.krdevops.springai.model.VersionCapability;
import com.krdevops.springai.service.security.template.SecurityTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SecuritySpec → FilePlan 목록 조립기.
 *
 * <p>단일 securityType과 조합 키워드({@code setup-*}) 모두 지원한다.
 * 조합 키워드는 {@link #expand(String, VersionCapability)}로 확장한 뒤
 * 각 타입을 {@link #toPlan(String, SecuritySpec)}으로 변환한다.
 * 중복 경로는 {@code LinkedHashSet}으로 제거한다.
 *
 * <p>하위 호환 문자열 반환은 {@link #renderSingle(SecuritySpec)}을 사용한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityFilePlanFactory {

    private final SecurityTemplateRenderer renderer;

    // 4.3 전용 조합 키워드 세트 (버전 불일치 검증용)
    private static final Set<String> V43_COMBOS = Set.of(
            "setup-war-43", "setup-war-43-xml", "setup-war-43-java",
            "setup-all-war-43", "setup-all-war-43-xml", "setup-all-war-43-java",
            "setup-handlers-43"
    );

    // 5.0 전용 조합 키워드 세트 (버전 불일치 검증용)
    private static final Set<String> V50_COMBOS = Set.of(
            "setup-war-50",
            "setup-all-war-50"
    );

    // -------------------------------------------------------------------------
    // 공개 API
    // -------------------------------------------------------------------------

    /**
     * 단일 securityType에 대한 템플릿 문자열을 반환한다.
     * Phase 1 하위 호환 경로에서 호출된다.
     *
     * @throws IllegalArgumentException 지원하지 않는 securityType인 경우
     */
    public String renderSingle(SecuritySpec spec) {
        return renderer.render(spec.securityType(), spec);
    }

    /**
     * securityType(단일/조합)을 FilePlan 목록으로 변환한다.
     * 조합 키워드는 구성 타입으로 확장된 후 변환된다.
     * 중복 경로는 LinkedHashSet으로 제거된다.
     *
     * @throws IllegalArgumentException 지원하지 않는 securityType인 경우
     */
    public List<FilePlan> plan(SecuritySpec spec) {
        List<String> types = expand(spec.securityType(), spec.capability());

        // 중복 경로 제거를 위해 LinkedHashSet으로 수집
        Set<String> seen = new LinkedHashSet<>();
        List<FilePlan> plans = new ArrayList<>();
        for (String type : types) {
            FilePlan fp = toPlan(type, spec);
            if (seen.add(fp.relativePath())) {
                plans.add(fp);
            }
        }
        return List.copyOf(plans);
    }

    // -------------------------------------------------------------------------
    // 조합 키워드 확장
    // -------------------------------------------------------------------------

    /**
     * securityType이 조합 키워드이면 구성 타입 목록으로 확장하고,
     * 단일 타입이면 그대로 반환한다.
     */
    List<String> expand(String securityType, VersionCapability cap) {
        String lower = securityType.toLowerCase();

        // 버전 불일치 검증 — 4.3 전용 키워드를 5.0에서 사용하거나 그 반대 방지
        if (V43_COMBOS.contains(lower) && cap.jakarta()) {
            throw new IllegalArgumentException(
                    lower + "은 egovVersion=4.3 전용입니다. " +
                    "5.0 셋업에는 setup-war-50 또는 setup-all-war-50을 사용하세요.");
        }
        if (V50_COMBOS.contains(lower) && !cap.jakarta()) {
            throw new IllegalArgumentException(
                    lower + "은 egovVersion=5.0 전용입니다. " +
                    "4.3 셋업에는 setup-war-43-xml 또는 setup-all-war-43-xml을 사용하세요.");
        }

        return switch (lower) {
            // 4.3 XML Security 방식 (공공 SI 표준) — setup-war-43은 하위 호환 alias
            case "setup-war-43", "setup-war-43-xml" -> war43XmlTypes();
            // 4.3 Java Config 방식 (WebSecurityConfigurerAdapter)
            case "setup-war-43-java" -> war43JavaTypes();
            // 5.0
            case "setup-war-50" -> war50Types();
            // 필터 조합
            case "setup-filters" -> filterTypes();
            // 4.3 핸들러 조합
            case "setup-handlers-43" -> handler43Types();
            // 4.3 XML 전체 (war43XmlTypes + securityMapper) — setup-all-war-43은 하위 호환 alias
            // ℹ️ filterTypes() 별도 추가 불필요: war43XmlTypes()에 이미 포함됨
            case "setup-all-war-43", "setup-all-war-43-xml" -> {
                List<String> all = new ArrayList<>(war43XmlTypes());
                all.add("securitymapper");
                yield distinct(all);
            }
            // 4.3 Java Config 전체 (Java Config + 핸들러 + 필터 + securityMapper)
            case "setup-all-war-43-java" -> {
                List<String> all = new ArrayList<>();
                all.addAll(war43JavaTypes());
                all.addAll(filterTypes());
                all.add("securitymapper");
                yield distinct(all);
            }
            // 5.0 전체
            case "setup-all-war-50" -> {
                List<String> all = new ArrayList<>();
                all.addAll(war50Types());
                all.addAll(filterTypes());
                all.add("accessdeniedhandler");
                all.add("securitymapper");
                yield distinct(all);
            }
            default -> List.of(lower);
        };
    }

    /** 4.3 XML Security 방식 (context-security.xml <http> 기반)
     *  ⚠️ javaConfig(Java Config)와 동시 사용 불가 — springSecurityFilterChain 충돌
     *  ⚠️ rolehierarchy Java Config 미포함 이유:
     *     context-security.xml이 roleHierarchy Bean을 XML로 직접 선언함.
     *     Java Config와 동시 로드 시 Bean 중복으로 애플리케이션 기동 실패 가능.
     *     동적 DB 기반 RoleHierarchyConfig.java가 필요하면 단독 securityType으로 생성할 것.
     *  ℹ️ userdetailsservice 포함 이유:
     *     egov43/context-security.xml의 egovAuthenticationProvider가
     *     ref="egovUserDetailsServiceImpl"로 이 Bean을 직접 참조함.
     *  ℹ️ loginFilter/logoutFilter/loginPolicyFilter 포함 이유:
     *     webXmlFilter(web.xml.fragment)가 이 3개 클래스를 직접 참조하므로
     *     조합이 생성하는 모든 파일은 그 조합 안에서 참조가 완결되어야 한다는 원칙에 따라 포함.
     *     미포함 시 런타임 ClassNotFoundException 발생. */
    private static List<String> war43XmlTypes() {
        return List.of(
                "webxmlfilter",
                "contextsecurity",
                "userdetailsservice",
                "sessionmapping",
                "loginfilter",
                "logoutfilter",
                "loginpolicyfilter",
                "loginpage",
                "userdetailshelperxml");
    }

    /** 타입 목록에서 순서를 유지하며 중복을 제거한다. */
    private static List<String> distinct(List<String> types) {
        return new ArrayList<>(new LinkedHashSet<>(types));
    }

    /** 4.3 Java Config 방식 (WebSecurityConfigurerAdapter 기반)
     *  ⚠️ contextSecurity(XML <http>)와 동시 사용 불가 */
    private static List<String> war43JavaTypes() {
        return List.of(
                "javaconfig",
                "userdetailsservice",
                "rolehierarchy",
                "successhandler",
                "failurehandler",
                "accessdeniedhandler",
                "loginpage");
    }

    private static List<String> war50Types() {
        return List.of(
                "contextsecurity",
                "javaconfig",
                "rolehierarchy",
                "loginpage",
                "userdetailshelperxml");
    }

    private static List<String> filterTypes() {
        return List.of(
                "loginfilter",
                "logoutfilter",
                "loginpolicyfilter",
                "sessionmapping");
    }

    private static List<String> handler43Types() {
        return List.of(
                "successhandler",
                "failurehandler",
                "accessdeniedhandler");
    }

    // -------------------------------------------------------------------------
    // 단일 타입 → FilePlan 변환
    // -------------------------------------------------------------------------

    /**
     * 단일 securityType을 저장 경로와 렌더러가 연결된 FilePlan으로 변환한다.
     *
     * @throws IllegalArgumentException 지원하지 않는 securityType인 경우
     */
    FilePlan toPlan(String type, SecuritySpec spec) {
        String pkg = spec.packagePath();
        return switch (type) {
            case "webxmlfilter" -> FilePlan.of(
                    "src/main/webapp/WEB-INF/web.xml.fragment",
                    FilePlan.FileKind.WEB,
                    () -> renderer.render(type, spec));
            case "contextsecurity" -> FilePlan.of(
                    "src/main/resources/egovframework/spring/context-security.xml",
                    FilePlan.FileKind.CONFIG,
                    () -> renderer.render(type, spec));
            case "securitymapper" -> FilePlan.of(
                    "src/main/resources/egovframework/sqlmap/security/security-mapper.sql",
                    FilePlan.FileKind.RESOURCE,
                    () -> renderer.render(type, spec));
            case "javaconfig" -> FilePlan.of(
                    "src/main/java/" + pkg + "/config/EgovProjectSecurityConfig.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "userdetailsservice" -> spec.capability().jakarta()
                    // 5.0: 안내 메시지를 .md 파일로 저장 (java 파일 생성 불필요)
                    ? FilePlan.of(
                            "docs/security/user-details-service-notice.md",
                            FilePlan.FileKind.META,
                            () -> renderer.render(type, spec))
                    // 4.3: 실제 구현체 java 파일 저장
                    : FilePlan.of(
                            "src/main/java/" + pkg + "/sec/service/impl/EgovUserDetailsServiceImpl.java",
                            FilePlan.FileKind.SOURCE,
                            () -> renderer.render(type, spec));
            case "rolehierarchy" -> FilePlan.of(
                    "src/main/java/" + pkg + "/sec/config/EgovRoleHierarchyConfig.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "loginfilter" -> FilePlan.of(
                    "src/main/java/" + pkg + "/sec/filter/EgovSpringSecurityLoginFilter.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "logoutfilter" -> FilePlan.of(
                    "src/main/java/" + pkg + "/sec/filter/EgovSpringSecurityLogoutFilter.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "loginpolicyfilter" -> FilePlan.of(
                    "src/main/java/" + pkg + "/uat/uap/filter/EgovLoginPolicyFilter.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "sessionmapping" -> FilePlan.of(
                    "src/main/java/" + pkg + "/uat/uia/service/impl/EgovSessionMapping.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "successhandler" -> FilePlan.of(
                    "src/main/java/" + pkg + "/sec/handler/EgovAuthenticationSuccessHandler.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "failurehandler" -> FilePlan.of(
                    "src/main/java/" + pkg + "/sec/handler/EgovAuthenticationFailureHandler.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "accessdeniedhandler" -> FilePlan.of(
                    "src/main/java/" + pkg + "/sec/handler/EgovAccessDeniedHandler.java",
                    FilePlan.FileKind.SOURCE,
                    () -> renderer.render(type, spec));
            case "loginpage" -> FilePlan.of(
                    "src/main/webapp/WEB-INF/jsp/egovframework/com/uat/uia/egovLoginUsr.jsp",
                    FilePlan.FileKind.WEB,
                    () -> renderer.render(type, spec));
            case "userdetailshelper" -> FilePlan.of(
                    "docs/security/user-details-helper-example.md",
                    FilePlan.FileKind.META,
                    () -> renderer.render(type, spec));
            case "userdetailshelperxml" -> FilePlan.of(
                    "src/main/resources/egovframework/spring/context-egovuserdetailshelper.xml",
                    FilePlan.FileKind.CONFIG,
                    () -> renderer.render(type, spec));
            default -> throw new IllegalArgumentException("지원하지 않는 securityType: " + type);
        };
    }
}
