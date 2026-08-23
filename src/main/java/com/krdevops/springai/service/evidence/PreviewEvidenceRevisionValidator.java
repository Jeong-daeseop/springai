package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Evidence Bundle의 Operation·Source Revision 정합성을 검증한다. */
@Service
public class PreviewEvidenceRevisionValidator {
    public ValidationResult validate(PreviewEvidenceBundle bundle, String expectedOperationId, String expectedSourceRevision) {
        if (bundle == null) throw new IllegalArgumentException("bundle은 필수입니다.");
        List<String> issues = new ArrayList<>();
        if (expectedOperationId != null && !expectedOperationId.equals(bundle.operationId())) issues.add("EVIDENCE_OPERATION_MISMATCH");
        if (expectedSourceRevision != null && !expectedSourceRevision.equals(bundle.sourceRevision())) issues.add("EVIDENCE_SOURCE_REVISION_MISMATCH");
        for (VersionedArtifactReference reference : bundle.references()) {
            if (reference.sourceRevision() != null && !bundle.sourceRevision().equals(reference.sourceRevision())) {
                issues.add("EVIDENCE_REFERENCE_REVISION_MISMATCH:" + reference.artifactId());
            }
        }
        return new ValidationResult(issues);
    }

    public void requireValid(PreviewEvidenceBundle bundle, String operationId, String sourceRevision) {
        ValidationResult result = validate(bundle, operationId, sourceRevision);
        if (!result.valid()) throw new IllegalStateException("Evidence Revision 검증 실패: " + result.issues());
    }

    public record ValidationResult(List<String> issues) {
        public ValidationResult { issues = List.copyOf(issues == null ? List.of() : issues); }
        public boolean valid() { return issues.isEmpty(); }
    }
}
