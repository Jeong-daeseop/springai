package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Spring Boot 프로젝트의 인터셉터 등록을 담당한다 — WAR 의 {@link ServletContextConfigurer}(servlet-context.xml
 * patch)에 대응하는 Boot 경로. {@code servlet-context.xml} 이 없는 Boot 에서는 {@code @Configuration} +
 * {@code WebMvcConfigurer} 자바 클래스({@code {packageName}.config.EgovWebMvcConfig})를 생성해
 * {@code EgovGnbMenuInterceptor} 를 등록한다.
 *
 * <p>멱등: 대상 클래스가 이미 있고 {@code EgovGnbMenuInterceptor} 를 참조하면 보존(skip).
 * 사용자가 만든 다른 {@code EgovWebMvcConfig} 가 있는데 우리 인터셉터가 없으면 자동 편집하지 않고
 * 수동 등록 안내만 반환한다(자바 소스 자동 편집은 부작용이 크다).
 *
 * <p>메인 화면 진입점({@code "/"} 매핑)은 이 클래스가 만들지 않는다 — Boot+thymeleaf 는
 * {@code ProjectInitializrTool} 이 생성하는 {@code MainController} 가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class BootMvcConfigConfigurer {

    private static final String INTERCEPTOR_SIMPLE_NAME = "EgovGnbMenuInterceptor";

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final OperationHashFactory hashFactory;

    public InterceptorRegistrationResult configure(Path outputPath, String packageName) {
        String relativePath = "src/main/java/" + packageName.replace('.', '/')
                + "/config/EgovWebMvcConfig.java";
        Path configPath = outputPath.resolve(relativePath).normalize();

        if (!Files.exists(configPath)) {
            String source = renderConfigClass(packageName);
            String writeFailure = write(outputPath, relativePath, null, source);
            if (writeFailure != null) {
                return new InterceptorRegistrationResult(
                        "  실패: " + configPath + " 쓰기 오류 — " + writeFailure + "\n", true);
            }
            return new InterceptorRegistrationResult(
                    "  생성: " + configPath + " — WebMvcConfigurer로 EgovGnbMenuInterceptor 등록 "
                  + "(addPathPatterns \"/**\", GnbMenuMapper 생성자 주입)\n",
                    false);
        }

        String existing;
        try {
            existing = Files.readString(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new InterceptorRegistrationResult(
                    "  실패: " + configPath + " 읽기 오류 — " + e.getMessage() + "\n", true);
        }

        if (existing.contains(INTERCEPTOR_SIMPLE_NAME)) {
            return new InterceptorRegistrationResult(
                    "  보존: " + configPath + " (EgovGnbMenuInterceptor 이미 등록됨)\n", false);
        }

        return new InterceptorRegistrationResult(
                "  수동 등록 필요: " + configPath + " 이 이미 있으나 EgovGnbMenuInterceptor 등록이 없습니다. "
              + "addInterceptors()에 "
              + "registry.addInterceptor(new " + packageName + ".cmm.web.EgovGnbMenuInterceptor(gnbMenuMapper))"
              + ".addPathPatterns(\"/**\") 를 직접 추가하세요.\n",
                false);
    }

    private String renderConfigClass(String packageName) {
        return """
                package %1$s.config;

                import %1$s.cmm.service.GnbMenuMapper;
                import %1$s.cmm.web.EgovGnbMenuInterceptor;
                import lombok.RequiredArgsConstructor;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                /**
                 * GNB 동적 메뉴 인터셉터 등록 (Spring Boot 경로).
                 * WAR 의 servlet-context.xml &lt;mvc:interceptors&gt; 에 대응한다.
                 * @author Claude AI
                 */
                @Configuration
                @RequiredArgsConstructor
                public class EgovWebMvcConfig implements WebMvcConfigurer {

                    private final GnbMenuMapper gnbMenuMapper;

                    @Override
                    public void addInterceptors(InterceptorRegistry registry) {
                        registry.addInterceptor(new EgovGnbMenuInterceptor(gnbMenuMapper))
                                .addPathPatterns("/**");
                    }
                }
                """.formatted(packageName);
    }

    /** @return 실패 상세 메시지, 성공하면 {@code null} */
    private String write(Path outputPath, String relativePath, String baseHash, String content) {
        codeService.validateOutputRoot(outputPath.toString());
        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath.toString(), null,
                List.of(new ProjectChangeSet.FileChange(relativePath, baseHash, content, null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);
        if (outcome.status() == ApplyOutcome.Status.APPLIED) {
            return null;
        }
        return switch (outcome.status()) {
            case CONFLICT -> "적용 직전 파일이 변경됨: " + outcome.conflictingPaths();
            case ROLLED_BACK -> outcome.failureDetail();
            case ROLLBACK_FAILED -> "복구까지 실패함(" + outcome.failureDetail()
                    + ") — 원본 상태로 안 돌아갔을 수 있습니다: " + outcome.failureMessages();
            default -> "알 수 없는 결과: " + outcome.status();
        };
    }

    /** {@link ServletContextConfigurer.ServletContextPatchResult} 와 동형 — 결과 포맷터가 공용으로 쓴다. */
    public record InterceptorRegistrationResult(String message, boolean failed) {
    }
}
