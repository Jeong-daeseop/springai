package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.service.GeneratedCodeContractAuditor;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifier;
import com.krdevops.springai.service.generation.pipeline.VerificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 생성 산출물의 공통 계약 감사 — 기존 {@link GeneratedCodeContractAuditor#audit} 위임.
 *
 * <p>명세서 §10.6/§11.1 표는 이 감사를 {@code PRE_VERIFY}로 적었지만, WP-0의
 * {@code CrudOrchestrationProcessorOrderTest}가 실측한 실제 호출 순서는
 * "Directory 검증 → Contract 감사"다. {@code ORT-PRN-005}(기존 동작 보존)가 표기보다 우선하므로
 * 이 Verifier를 {@code VERIFY}(= Directory의 {@code PRE_VERIFY}보다 뒤)에 배정한다.
 */
@Component
@RequiredArgsConstructor
public class CommonGeneratedContractVerifier implements GenerationVerifier {

    private final GeneratedCodeContractAuditor generatedCodeContractAuditor;

    @Override
    public String id() {
        return "commonGeneratedContractVerifier";
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.VERIFY;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public VerificationResult verify(GenerationProcessingContext context) {
        List<String> contractFailures =
                generatedCodeContractAuditor.audit(context.context().outputPath());
        if (contractFailures.isEmpty()) {
            return VerificationResult.none();
        }
        return new VerificationResult(
                "\n\n[생성 계약 감사]\n" + String.join("\n", contractFailures),
                contractFailures.stream()
                        .map(value -> new GenerationFailure("contractAudit", "생성 계약 감사 — " + value))
                        .toList());
    }
}
