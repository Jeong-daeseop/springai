package com.krdevops.springai.service.contract;

import com.krdevops.springai.model.contract.GenerationIssue;
import org.springframework.stereotype.Component;

/** R1-014 계열 서비스가 일관된 형태로 {@link GenerationIssue}를 생성하도록 돕는 공통 factory. */
@Component
public class GenerationIssueFactory {

    public GenerationIssue fatal(String code, String stage, String message) {
        return issue(code, GenerationIssue.Severity.FATAL, stage, null, message, null);
    }

    public GenerationIssue error(String code, String stage, String message, String sourceLocation) {
        return issue(code, GenerationIssue.Severity.ERROR, stage, sourceLocation, message, null);
    }

    public GenerationIssue warning(
            String code, String stage, String message, String sourceLocation, String remediation) {
        return issue(code, GenerationIssue.Severity.WARNING, stage, sourceLocation, message, remediation);
    }

    public GenerationIssue issue(
            String code,
            GenerationIssue.Severity severity,
            String stage,
            String sourceLocation,
            String message,
            String remediation
    ) {
        return new GenerationIssue(code, severity, stage, sourceLocation, message, remediation);
    }
}
