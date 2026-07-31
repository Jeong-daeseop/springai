package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.service.CodeValidatorService;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifier;
import com.krdevops.springai.service.generation.pipeline.VerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 생성 디렉터리 구문·규칙 검증 — 기존 {@link CodeValidatorService#validateDirectory} 위임.
 *
 * <p>WP-0 실측상 이 검증이 Common Contract 감사보다 <b>먼저</b> 실행되므로 {@code PRE_VERIFY}에
 * 배정한다({@link CommonGeneratedContractVerifier}의 Javadoc 참고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeDirectoryVerifier implements GenerationVerifier {

    private final CodeValidatorService codeValidatorService;

    @Override
    public String id() {
        return "codeDirectoryVerifier";
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.PRE_VERIFY;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public VerificationResult verify(GenerationProcessingContext context) {
        String outputPath = context.context().outputPath();
        try {
            return VerificationResult.summary(codeValidatorService.validateDirectory(outputPath));
        } catch (Exception e) {
            log.warn("[pipeline] 코드 검증 실패: {}", e.getMessage());
            return VerificationResult.summary("검증 실패: " + e.getMessage());
        }
    }
}
