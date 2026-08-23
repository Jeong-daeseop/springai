package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignSystemKnowledgeSnapshot;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class DesignSystemKnowledgeSnapshotRepository {
    private final Map<String, DesignSystemKnowledgeSnapshot> snapshots = new ConcurrentHashMap<>();
    public DesignSystemKnowledgeSnapshot save(DesignSystemKnowledgeSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasValidContentHash()) throw new IllegalArgumentException("Snapshot Hash가 유효하지 않습니다.");
        if (snapshots.putIfAbsent(snapshot.snapshotId()+"@"+snapshot.version(), snapshot) != null) throw new IllegalStateException("Snapshot Version이 이미 존재합니다.");
        return snapshot;
    }
    public Optional<DesignSystemKnowledgeSnapshot> find(String id, String version) { return Optional.ofNullable(snapshots.get(id+"@"+version)); }
}
