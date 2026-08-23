package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

/** Evidence Bundle에 디자인·화면 업무 계약 Reference가 모두 결합됐는지 검증한다. */
@Service
public class PreviewEvidenceReferenceValidator {
    private static final Set<String> REQUIRED_TYPES = Set.of("DESIGN_SYSTEM", "UI_DESIGN_SPEC", "SCREEN_SPECIFICATION");
    public ValidationResult validate(PreviewEvidenceBundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle은 필수입니다.");
        Set<String> present = bundle.references().stream().map(reference -> reference.artifactType()).collect(java.util.stream.Collectors.toSet());
        return new ValidationResult(REQUIRED_TYPES.stream().filter(type -> !present.contains(type)).sorted().toList());
    }
    public record ValidationResult(List<String> missingTypes) {
        public ValidationResult { missingTypes = List.copyOf(missingTypes == null ? List.of() : missingTypes); }
        public boolean valid() { return missingTypes.isEmpty(); }
    }
}
