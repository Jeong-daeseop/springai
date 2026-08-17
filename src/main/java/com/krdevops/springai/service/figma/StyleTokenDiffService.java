package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.designsystem.ComponentBinding;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableBinding;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R5-045: 참조 Figma 파일에서 추출한 Style Token 후보({@link FigmaStyleExtractor})와 운영
 * {@link DesignSystemProfile}의 Token({@code variables})의 차이를 계산한다. 이 서비스는 조회만
 * 하며 Profile이나 Figma Library를 절대 쓰지 않는다 — 자동 반영은 사람이 별도 절차로 승인한다.
 */
@Service
public class StyleTokenDiffService {

    public StyleTokenDiffResult diff(FigmaStyleExtractor.DesignTokenExtraction candidates, DesignSystemProfile profile) {
        Map<String, VariableBinding> profileVariables =
                profile == null || profile.variables() == null ? Map.of() : profile.variables();
        List<StyleTokenDiffEntry> entries = new ArrayList<>();
        for (String tokenName : candidateTokenNames(candidates)) {
            entries.add(diffEntry(tokenName, profileVariables.get(tokenName)));
        }
        return new StyleTokenDiffResult(entries);
    }

    private StyleTokenDiffEntry diffEntry(String tokenName, VariableBinding binding) {
        if (binding == null) {
            return new StyleTokenDiffEntry(tokenName, StyleTokenDiffStatus.NEW_CANDIDATE, null, null);
        }
        if (binding.status() != ComponentBinding.BindingStatus.BOUND) {
            return new StyleTokenDiffEntry(
                    tokenName, StyleTokenDiffStatus.UNBOUND_IN_PROFILE, binding.variableId(), binding.status().name());
        }
        return new StyleTokenDiffEntry(
                tokenName, StyleTokenDiffStatus.MATCHED, binding.variableId(), binding.status().name());
    }

    private List<String> candidateTokenNames(FigmaStyleExtractor.DesignTokenExtraction candidates) {
        List<String> names = new ArrayList<>();
        if (candidates == null) {
            return names;
        }
        for (var color : candidates.colors()) {
            names.add(normalize(color.normalizedName() != null ? color.normalizedName() : color.name()));
        }
        for (var typography : candidates.typographies()) {
            names.add(normalize(typography.name()));
        }
        for (var spacing : candidates.spacings()) {
            names.add(normalize(spacing.name()));
        }
        for (var radius : candidates.radii()) {
            names.add(normalize(radius.name()));
        }
        return names;
    }

    private String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).trim().replace(" ", "-").replace("/", "_");
    }

    public enum StyleTokenDiffStatus {
        /** 후보 이름이 Profile에 이미 있고 실제 Figma Variable에 BOUND돼 있다. */
        MATCHED,
        /** 후보 이름이 Profile에 없다 — 새 Token 후보. */
        NEW_CANDIDATE,
        /** 후보 이름이 Profile에 있지만 아직 Figma Variable에 바인딩되지 않았다(UNBOUND/DEPRECATED). */
        UNBOUND_IN_PROFILE
    }

    public record StyleTokenDiffEntry(
            String tokenName, StyleTokenDiffStatus status, String variableId, String profileBindingStatus) {
    }

    /** {@code autoLibraryChangeApplied}는 항상 false로 고정된 상수다 — 이 서비스는 Library를 쓰지 않는다. */
    public record StyleTokenDiffResult(List<StyleTokenDiffEntry> entries) {
        public static final boolean AUTO_LIBRARY_CHANGE_APPLIED = false;

        public long newCandidateCount() {
            return entries.stream().filter(entry -> entry.status() == StyleTokenDiffStatus.NEW_CANDIDATE).count();
        }
    }
}
