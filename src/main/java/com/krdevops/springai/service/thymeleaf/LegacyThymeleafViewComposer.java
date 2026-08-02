package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.thymeleaf.BoundThymeleafView;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafSkeleton;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * I-4C 2단계: {@link ThymeleafSkeleton}(구조)과 {@link ThymeleafBindingContract}(값)을 결합한다.
 * 둘의 screenId/screenRole이 다르면(서로 다른 화면을 잘못 짝지은 프로그래밍 오류) FATAL로 실패한다.
 */
@Service
public class LegacyThymeleafViewComposer {

    private static final String STAGE = "SKELETON_BINDING";

    private final GenerationIssueFactory issueFactory;

    public LegacyThymeleafViewComposer(GenerationIssueFactory issueFactory) {
        this.issueFactory = issueFactory;
    }

    public ThymeleafGenerationStageResult<BoundThymeleafView> compose(
            ThymeleafSkeleton skeleton, ThymeleafBindingContract contract) {
        try {
            BoundThymeleafView view = new BoundThymeleafView(skeleton, contract);
            return ThymeleafGenerationStageResult.success(view, contract.issues());
        } catch (IllegalArgumentException exception) {
            GenerationIssue issue = issueFactory.issue(
                    "SKELETON_CONTRACT_MISMATCH", GenerationIssue.Severity.FATAL, STAGE,
                    contract.screenId(), exception.getMessage(), null);
            return ThymeleafGenerationStageResult.failure(List.of(issue));
        }
    }
}
