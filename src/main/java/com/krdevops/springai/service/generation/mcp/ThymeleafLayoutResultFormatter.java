package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult.FileOutcome;
import org.springframework.stereotype.Component;

/**
 * {@link LayoutGenerationResult}를 기존 {@code ThymeleafLayoutTool}의 {@code StringBuilder}
 * 조립 로직과 바이트 단위로 동일한 MCP 응답 문자열로 변환한다.
 */
@Component
public class ThymeleafLayoutResultFormatter {

    public String format(LayoutGenerationResult result, boolean packageNameMissing) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Thymeleaf layout 생성 결과 ===\n\n");
        if (packageNameMissing) {
            sb.append("⚠ packageName 미지정 — 기본값 '").append(result.resolvedPackageName()).append("' 사용. ")
              .append("실제 프로젝트 packageName과 다르면 GNB 컴포넌트가 컴파일되지 않거나 등록되지 않습니다.\n\n");
        }
        sb.append("출력 경로: ").append(result.outputPath()).append("\n");
        sb.append("프로젝트 타입: ").append(result.projectType()).append("\n");
        sb.append("layoutBasePath: ").append(result.resolvedBasePath()).append("\n");
        sb.append("packageName: ").append(result.resolvedPackageName()).append("\n\n");
        sb.append("menuTableName: ").append(result.resolvedMenuTableName()).append("\n");
        sb.append("programTableName: ").append(result.resolvedProgramTableName()).append("\n\n");

        for (FileOutcome outcome : result.layoutFileOutcomes()) {
            sb.append(formatFileOutcome(outcome));
        }

        sb.append(result.logoResultLine());

        for (FileOutcome outcome : result.gnbComponentOutcomes()) {
            sb.append(formatFileOutcome(outcome));
        }

        sb.append(formatFileOutcome(result.mainHtmlOutcome()));

        ThymeleafLayoutValidator.LayoutValidationResult validation = result.validation();
        if (!validation.valid()) {
            sb.append("\n[검증 실패]\n")
              .append(String.join("\n", validation.missingFiles()))
              .append("\n");
        } else {
            sb.append("\n[검증 완료] layout 5종이 모두 존재합니다.\n");
        }

        boolean boot = "BOOT".equals(result.projectType());

        sb.append(boot ? "\n[WebMvcConfigurer 인터셉터 등록]\n" : "\n[servlet-context.xml 등록]\n");
        sb.append(result.servletContextPatchMessage());

        sb.append("\n[MyBatis Mapper 설정]\n");
        MyBatisRuntimeConfigurer.ConfigurationResult myBatis = result.myBatisResult();
        if (myBatis.skipped()) {
            sb.append("  생략: ").append(myBatis.path()).append(" — ").append(myBatis.message());
            if (boot) {
                sb.append(" (Boot는 application.yml의 mybatis.mapper-locations + @MapperScan으로 GnbMenuMapper를 배선합니다)");
            }
            sb.append("\n");
        } else {
            sb.append("  ").append(myBatis.success() ? "완료: " : "실패: ")
                    .append(myBatis.path()).append(" — ").append(myBatis.message()).append("\n");
        }

        sb.append("\n[Thymeleaf 런타임 설정]\n");
        if (boot) {
            sb.append("  Boot auto-configuration이 ViewResolver/LayoutDialect를 자동 구성합니다 ")
              .append("(build 파일의 spring-boot-starter-thymeleaf + thymeleaf-layout-dialect 의존성 필요 — ")
              .append("initializeProject가 viewType=thymeleaf일 때 추가).\n");
            return sb.toString();
        }
        if (result.runtimeSkipped()) {
            sb.append("  건너뜀: servlet-context.xml patch 실패 상태라 ViewResolver 보강을 생략합니다.\n");
            return sb.toString();
        }

        if (result.runtimeFailures().isEmpty()) {
            sb.append("  완료: eGovFrame ").append(result.egovVersion())
              .append(" 기준 Thymeleaf ViewResolver/classpath:/templates 런타임 설정을 확인했습니다.\n");
        } else {
            result.runtimeFailures().forEach(failure -> sb.append("  실패: ").append(failure).append("\n"));
        }
        return sb.toString();
    }

    private String formatFileOutcome(FileOutcome outcome) {
        return switch (outcome.status()) {
            case PRESERVED -> "  보존: " + outcome.path() + "\n";
            case FAILED -> "  실패: " + outcome.path() + " — " + outcome.detail() + "\n";
            case CREATED -> "  생성: " + outcome.path() + "\n";
        };
    }
}
