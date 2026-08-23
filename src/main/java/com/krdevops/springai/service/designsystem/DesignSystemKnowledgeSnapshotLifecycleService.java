package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignSystemKnowledgeSnapshot;
import org.springframework.stereotype.Service;

@Service
public class DesignSystemKnowledgeSnapshotLifecycleService {
    public DesignSystemKnowledgeSnapshot approve(DesignSystemKnowledgeSnapshot snapshot) {
        require(snapshot);
        if (snapshot.status() != DesignSystemKnowledgeSnapshot.Status.DRAFT) throw new IllegalStateException("DRAFT Snapshot만 승인할 수 있습니다.");
        return new DesignSystemKnowledgeSnapshot(snapshot.snapshotId(), snapshot.version(), snapshot.contentHash(), DesignSystemKnowledgeSnapshot.Status.APPROVED, snapshot.references());
    }
    public void requireApproved(DesignSystemKnowledgeSnapshot snapshot) {
        require(snapshot);
        if (snapshot.status() != DesignSystemKnowledgeSnapshot.Status.APPROVED) throw new IllegalStateException("승인되지 않은 Knowledge Snapshot입니다.");
    }
    private static void require(DesignSystemKnowledgeSnapshot snapshot) { if (snapshot == null || !snapshot.hasValidContentHash()) throw new IllegalArgumentException("Snapshot이 유효하지 않습니다."); }
}
