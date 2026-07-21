package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 생성된 게시판의 URL·복합 PK·bbsId·FreeMarker/CSS 계약을 정적으로 감사한다. */
@Service
@RequiredArgsConstructor
public class BoardGeneratedCodeAuditor {

    private final MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;

    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("class\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Set<String> KRDS_SIZED_COMPONENTS = Set.of(
            "krds-input", "krds-form-select", "krds-btn");
    private static final Set<String> SIZE_MODIFIERS = Set.of("small", "medium", "large");
    private static final List<String> TEXT_CLASS_CONTRACT = List.of(
            ".egov-primary-text", ".egov-detail-link", ".egov-file-detail-link",
            ".egov-file-empty", ".egov-post-nav-link");

    public String audit(String outputPath, BoardTemplateModel model, CrudViewType viewType,
                        String layoutBasePath) {
        List<String> failures = new ArrayList<>();
        Path root = Path.of(outputPath);
        String pkg = model.packageName().replace('.', '/');
        Path controller = root.resolve("src/main/java").resolve(pkg)
                .resolve("web/Egov" + model.domain() + "Controller.java");
        String controllerCode = read(controller, failures);
        require(controllerCode, "hasCompositeKey", "Controller 복합 PK helper 누락", failures);
        require(controllerCode, "resolveBbsId", "Controller 기본 bbsId 해석 누락", failures);
        if (model.route().hasListAlias()) {
            require(controllerCode, '"' + model.route().registeredListPath() + '"',
                    "DB URL alias 누락", failures);
        }

        Path vo = root.resolve("src/main/java").resolve(pkg)
                .resolve("service/" + model.domain() + "VO.java");
        String voCode = read(vo, failures);
        auditGeneratedNttIdValidation(voCode, model.nttId().javaName(), failures);

        Path serviceImpl = root.resolve("src/main/java").resolve(pkg)
                .resolve("service/impl/Egov" + model.domain() + "ServiceImpl.java");
        String serviceCode = read(serviceImpl, failures);
        require(serviceCode, "set" + capitalize(model.nttId().javaName()) + "(",
                "Service 생성형 nttId 할당 누락", failures);
        if (!"String".equals(model.nttId().javaType())) {
            require(serviceCode, "selectNext" + model.domain() + "NttId()",
                    "Service 숫자형 nttId 안전 채번 호출 누락", failures);
        }

        Path mapper = root.resolve("src/main/resources/egovframework/mapper")
                .resolve(model.domainLc()).resolve(model.domain() + "Mapper.xml");
        String mapperCode = read(mapper, failures);
        if (mapperCode.contains("${")) failures.add("Mapper XML에 ${} 문자열 바인딩이 남아 있습니다.");
        if (!"String".equals(model.nttId().javaType())) {
            require(mapperCode, "selectNext" + model.domain() + "NttId",
                    "Mapper 숫자형 nttId 채번 쿼리 누락", failures);
            require(mapperCode, "FOR UPDATE", "Mapper 숫자형 nttId 채번 잠금 누락", failures);
        }
        MyBatisRuntimeConfigurer.ValidationResult myBatis = myBatisRuntimeConfigurer.validate(
                outputPath, model.packageName() + ".service.impl");
        if (!myBatis.valid()) {
            failures.add("MyBatis Mapper 런타임 설정 누락: " + myBatis.message());
        }

        if (viewType == CrudViewType.THYMELEAF) {
            Path templates = root.resolve("src/main/resources/templates");
            for (String suffix : List.of("List", "Detail", "Regist", "Updt")) {
                String html = read(templates.resolve(model.domainLc())
                        .resolve("Egov" + model.domain() + suffix + ".html"), failures);
                require(html, "bbsId", suffix + " 화면 bbsId 전달 계약 누락", failures);
                require(html, "egov-crud-page", suffix + " 화면 CRUD CSS scope 누락", failures);
                auditKrdsSizeModifiers(html, suffix, failures);
                if (List.of("Detail", "Regist", "Updt").contains(suffix)) {
                    auditThymeleafCsrf(html, suffix, failures);
                }
                if (html.contains("<#") || html.contains("${displayName}")) {
                    failures.add(suffix + " 화면에 FreeMarker 문법이 남아 있습니다.");
                }
            }
            auditCss(root, failures);
            String servletContext = read(root.resolve(
                    "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml"), failures);
            require(servletContext, model.packageName() + ".web",
                    "servlet-context.xml Controller component-scan 누락", failures);
            Path layout = templates.resolve(layoutBasePath);
            for (String file : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html", "footer.html")) {
                if (!Files.exists(layout.resolve(file))) failures.add("layout 파일 누락: " + layout.resolve(file));
            }
        } else {
            Path jspRoot = root.resolve("src/main/webapp/WEB-INF/jsp").resolve(model.domainLc());
            for (String suffix : List.of("Detail", "Regist", "Updt")) {
                String jsp = read(jspRoot.resolve("Egov" + model.domain() + suffix + ".jsp"), failures);
                require(jsp, "${not empty _csrf}", suffix + " JSP CSRF 조건 누락", failures);
                require(jsp, "${_csrf.parameterName}", suffix + " JSP CSRF 파라미터명 누락", failures);
                require(jsp, "${_csrf.token}", suffix + " JSP CSRF 토큰 누락", failures);
            }
        }
        return failures.isEmpty()
                ? "URL alias·bbsId·복합 PK·Mapper·View·CSS·CSRF·layout 감사 통과"
                : "감사 실패 " + failures.size() + "건: " + String.join(" | ", failures);
    }

    private void auditThymeleafCsrf(String html, String suffix, List<String> failures) {
        require(html, "${_csrf != null}", suffix + " 화면 CSRF 조건 누락", failures);
        require(html, "${_csrf.parameterName}", suffix + " 화면 CSRF 파라미터명 누락", failures);
        require(html, "${_csrf.token}", suffix + " 화면 CSRF 토큰 누락", failures);
    }

    private void auditGeneratedNttIdValidation(
            String voCode, String nttIdName, List<String> failures) {
        Pattern generatedIdValidation = Pattern.compile(
                "@(NotNull|NotBlank)\\b[\\s\\S]{0,200}?private\\s+[^;]+\\s+"
                        + Pattern.quote(nttIdName) + "\\s*;");
        if (generatedIdValidation.matcher(voCode).find()) {
            failures.add("VO 생성형 nttId에 입력 필수 검증이 남아 있습니다.");
        }
    }

    private void auditKrdsSizeModifiers(String html, String suffix, List<String> failures) {
        Matcher matcher = CLASS_ATTRIBUTE.matcher(html);
        while (matcher.find()) {
            Set<String> classes = new HashSet<>(List.of(matcher.group(1).trim().split("\\s+")));
            for (String component : KRDS_SIZED_COMPONENTS) {
                if (classes.contains(component) && classes.stream().noneMatch(SIZE_MODIFIERS::contains)) {
                    failures.add(suffix + " 화면 " + component + " 크기 modifier 누락: " + matcher.group());
                }
            }
        }
    }

    private void auditCss(Path root, List<String> failures) {
        Path css = firstExisting(
                root.resolve("src/main/webapp/resources/css/styles.css"),
                root.resolve("src/main/resources/static/resources/css/styles.css"));
        if (css == null) {
            failures.add("styles.css 파일 누락");
            return;
        }
        String source = read(css, failures);
        for (String token : List.of(
                KrdsStylesConfigurer.START_MARKER,
                "--egov-screen-control-height",
                "--egov-screen-textarea-min-height",
                "--egov-screen-link-font-size",
                "--krds-button--size-height-medium",
                "--krds-input--size-height-medium",
                "--krds-input--textarea-size-height",
                "--krds-form-select--size-height-medium",
                "--krds-table--data-tbody-padding")) {
            require(source, token, "styles.css 필수 계약 누락: " + token, failures);
        }
        String paginationRule = cssRuleBody(source, ".krds-pagination");
        require(paginationRule, "display: flex", "페이지네이션 flex 계약 누락", failures);
        require(paginationRule, "align-items: center", "페이지네이션 수직 정렬 계약 누락", failures);
        for (String selector : TEXT_CLASS_CONTRACT) {
            require(source, selector, "styles.css 텍스트 클래스 누락: " + selector, failures);
        }
    }

    private String cssRuleBody(String css, String selector) {
        Pattern rule = Pattern.compile(Pattern.quote(selector) + "\\s*\\{([^}]*)}", Pattern.DOTALL);
        Matcher matcher = rule.matcher(css);
        return matcher.find() ? matcher.group(1) : "";
    }

    private Path firstExisting(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }

    private String read(Path path, List<String> failures) {
        try {
            if (!Files.exists(path)) {
                failures.add("파일 누락: " + path);
                return "";
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            failures.add("파일 읽기 실패: " + path + " — " + e.getMessage());
            return "";
        }
    }

    private void require(String source, String token, String message, List<String> failures) {
        if (!source.contains(token)) failures.add(message);
    }

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
