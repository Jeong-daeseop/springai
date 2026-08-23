package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import org.springframework.stereotype.Service;

import java.util.List;

/** Binding·Security·Build·Render·A11y·Visual·Interaction Report를 Bundle 계약으로 조립한다. */
@Service
public class PreviewEvidenceReportAssembler {
    public PreviewEvidenceBundle.Reports reports(VersionedArtifactReference binding,
                                                  VersionedArtifactReference security,
                                                  VersionedArtifactReference build,
                                                  VersionedArtifactReference render,
                                                  VersionedArtifactReference accessibility,
                                                  VersionedArtifactReference visualDiff,
                                                  VersionedArtifactReference interactionFlow) {
        if (binding == null || build == null || render == null) {
            throw new IllegalArgumentException("Binding·Build·Render Report는 필수입니다.");
        }
        return new PreviewEvidenceBundle.Reports(binding, build, render, security, accessibility, visualDiff, interactionFlow);
    }

    public List<String> aggregateFallbacks(List<String> fallbackAssessments, List<String> warnings) {
        return java.util.stream.Stream.concat(
                fallbackAssessments == null ? java.util.stream.Stream.empty() : fallbackAssessments.stream(),
                warnings == null ? java.util.stream.Stream.empty() : warnings.stream())
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().sorted().toList();
    }
}
