package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

@Service
public class PreviewEvidenceGenerationReferenceValidator {
    private static final Set<String> REQUIRED = Set.of("RENDERER_PROFILE", "GENERATION_SCOPE", "GENERATION_OWNERSHIP");
    public List<String> missing(PreviewEvidenceBundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle은 필수입니다.");
        Set<String> present = bundle.references().stream().map(reference -> reference.artifactType()).collect(java.util.stream.Collectors.toSet());
        return REQUIRED.stream().filter(type -> !present.contains(type)).sorted().toList();
    }
    public void requireValid(PreviewEvidenceBundle bundle) {
        List<String> missing = missing(bundle);
        if (!missing.isEmpty()) throw new IllegalStateException("Evidence 생성 Reference 누락: " + missing);
    }
}
