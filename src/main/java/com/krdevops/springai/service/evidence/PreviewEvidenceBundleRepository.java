package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.krdevops.springai.service.pipeline.PipelineOperationGate;
import com.krdevops.springai.service.pipeline.PipelineActionAuthorization;

/** PreviewEvidenceBundle 불변 저장소. 동일 ID 재저장을 허용하지 않는다. */
@Repository
public class PreviewEvidenceBundleRepository {
    private final Map<String, PreviewEvidenceBundle> bundles = new ConcurrentHashMap<>();
    public PreviewEvidenceBundle save(PreviewEvidenceBundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle은 필수입니다.");
        if (!bundle.hasValidContentHash() || !bundle.hasValidAuditSnapshotHash()) throw new IllegalArgumentException("Evidence Bundle hash가 유효하지 않습니다.");
        if (bundles.putIfAbsent(bundle.bundleId(), bundle) != null) throw new IllegalStateException("Bundle ID가 이미 존재합니다: " + bundle.bundleId());
        return bundle;
    }
    public Optional<PreviewEvidenceBundle> find(String bundleId) { return Optional.ofNullable(bundles.get(bundleId)); }
    public PreviewEvidenceBundle saveAuthorized(PreviewEvidenceBundle bundle, PipelineOperationGate gate,
                                                PipelineActionAuthorization.AuthorizationContext context) {
        if (gate == null) throw new IllegalArgumentException("operation gate는 필수입니다.");
        gate.authorize("previewGenerationScope", context);
        return save(bundle);
    }
}
