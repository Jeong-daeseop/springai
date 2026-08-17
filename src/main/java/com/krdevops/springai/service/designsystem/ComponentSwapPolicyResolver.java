package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.contract.PlatformLayoutPolicy;
import org.springframework.stereotype.Service;

import java.util.List;

/** R1-015: {@link PlatformLayoutPolicy#componentSwaps()}를 실제로 소비해 플랫폼별 컴포넌트 대체를 해석한다. */
@Service
public class ComponentSwapPolicyResolver {

    public Resolution resolve(PlatformLayoutPolicy policy, String platform, String logicalType) {
        if (policy == null || platform == null || logicalType == null) {
            return new Resolution(logicalType, false, null);
        }
        List<PlatformLayoutPolicy.ComponentSwapRule> matches = policy.componentSwaps().stream()
                .filter(rule -> rule.platform().equals(platform) && rule.fromComponent().equals(logicalType))
                .toList();
        if (matches.isEmpty()) {
            return new Resolution(logicalType, false, null);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "COMPONENT_SWAP_AMBIGUOUS: " + logicalType + "@" + platform
                            + "에 규칙이 " + matches.size() + "건 존재합니다.");
        }
        PlatformLayoutPolicy.ComponentSwapRule rule = matches.get(0);
        return new Resolution(rule.toComponent(), true, rule.reason());
    }

    public record Resolution(String resolvedLogicalType, boolean swapped, String reason) {}
}
