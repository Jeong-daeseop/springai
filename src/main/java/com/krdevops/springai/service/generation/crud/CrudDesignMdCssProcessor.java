package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.thymeleaf.AppliedDesignRules;
import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.KrdsStylesConfigurer;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import com.krdevops.springai.service.thymeleaf.CompanyDesignTokenResolver;
import com.krdevops.springai.service.thymeleaf.DesignMdRuleLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CRUD auto 경로에 DESIGN.md 기준 KRDS 디자인 토큰을 반영한다.
 *
 * <p>claude 경로({@code CrudPromptBuilderService})는 안내 텍스트만 프롬프트에 넣으면 되지만,
 * auto 경로는 LLM이 없는 기계적 렌더링이라 실제로 {@code styles.css}를 patch해야 한다.
 * {@code designSystemProfileId}가 없으면(대부분의 기존 호출) {@link #supports}가 false를
 * 반환해 조용히 건너뛴다 — 기존 동작에 영향이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrudDesignMdCssProcessor implements GenerationStageProcessor {

    public static final String ID = "crudDesignMdCssProcessor";

    private final DesignMdRuleLoader designMdRuleLoader;
    private final CompanyDesignTokenResolver companyDesignTokenResolver;
    private final KrdsStylesConfigurer krdsStylesConfigurer;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.PRE_WRITE;
    }

    @Override
    public boolean supports(GenerationContext context) {
        String profileId = context.attribute(CrudGenerationAttributes.DESIGN_SYSTEM_PROFILE_ID);
        return profileId != null && !profileId.isBlank();
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        String outputPath = context.context().outputPath();
        String profileId = context.context().attribute(CrudGenerationAttributes.DESIGN_SYSTEM_PROFILE_ID);

        ThymeleafGenerationStageResult<AppliedDesignRules> rulesResult = designMdRuleLoader.load(outputPath);
        if (!rulesResult.successful()) {
            log.warn("[crud-design-md-css] DESIGN.md 파싱 실패, KRDS 토큰 반영 건너뜀: outputPath={}", outputPath);
            return ProcessorResult.ok();
        }

        ThymeleafGenerationStageResult<ResolvedDesignTokens> tokensResult =
                companyDesignTokenResolver.resolve(profileId, rulesResult.value());
        if (!tokensResult.successful()) {
            log.warn("[crud-design-md-css] 디자인 토큰 해석 실패, KRDS 토큰 반영 건너뜀: profileId={}", profileId);
            return ProcessorResult.ok();
        }

        KrdsStylesConfigurer.CssPatchResult patch =
                krdsStylesConfigurer.ensureDesignMdTokenStyles(outputPath, tokensResult.value());
        if (patch.failed()) {
            log.warn("[crud-design-md-css] CSS 반영 실패(non-fatal): {}", patch.message());
        }
        return ProcessorResult.ok();
    }
}
