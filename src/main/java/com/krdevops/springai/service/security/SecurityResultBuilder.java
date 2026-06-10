package com.krdevops.springai.service.security;

import com.krdevops.springai.model.GenerationReport;
import org.springframework.stereotype.Component;

/**
 * {@link GenerationReport}를 사람이 읽기 좋은 결과 문자열로 변환한다.
 * SecurityTemplateService Phase 2에서 outputPath 저장 결과 반환에 사용된다.
 */
@Component
public class SecurityResultBuilder {

    public String build(GenerationReport report) {
        if (report.hasErrors()) {
            return buildError(report);
        }
        return buildSuccess(report);
    }

    private String buildSuccess(GenerationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ Security 템플릿 저장 완료\n\n");
        sb.append("저장 경로: ").append(report.rootPath()).append("\n");
        sb.append("생성 파일: ").append(report.created().size()).append("개\n\n");
        for (String path : report.created()) {
            sb.append("  + ").append(path).append("\n");
        }
        if (!report.warnings().isEmpty()) {
            sb.append("\n⚠️ 경고:\n");
            for (String w : report.warnings()) {
                sb.append("  - ").append(w).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildError(GenerationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("❌ Security 템플릿 저장 실패\n\n");
        sb.append("저장 경로: ").append(report.rootPath()).append("\n");
        if (!report.created().isEmpty()) {
            sb.append("저장된 파일: ").append(report.created().size()).append("개\n");
            for (String path : report.created()) {
                sb.append("  + ").append(path).append("\n");
            }
        }
        sb.append("\n오류:\n");
        for (var entry : report.errors().entrySet()) {
            sb.append("  ✗ ").append(entry.getKey())
              .append(" — ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}
