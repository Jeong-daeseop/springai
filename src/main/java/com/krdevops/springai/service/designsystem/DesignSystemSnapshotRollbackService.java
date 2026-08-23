package com.krdevops.springai.service.designsystem;
import com.krdevops.springai.model.designsystem.DesignSystemKnowledgeSnapshot;
import org.springframework.stereotype.Service;
@Service public class DesignSystemSnapshotRollbackService { private final DesignSystemKnowledgeSnapshotRepository repo; public DesignSystemSnapshotRollbackService(DesignSystemKnowledgeSnapshotRepository repo){this.repo=repo;} public DesignSystemKnowledgeSnapshot requireApproved(String id,String version){DesignSystemKnowledgeSnapshot s=repo.find(id,version).orElseThrow(()->new IllegalArgumentException("Snapshot을 찾을 수 없습니다.")); if(s.status()!=DesignSystemKnowledgeSnapshot.Status.APPROVED)throw new IllegalStateException("승인된 Snapshot만 Rollback할 수 있습니다."); return s;} }
