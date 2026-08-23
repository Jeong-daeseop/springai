package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignSystemKnowledgeSnapshot;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DesignSystemKnowledgeIndex {
    public List<String> search(DesignSystemKnowledgeSnapshot snapshot, String query) {
        if (snapshot == null || snapshot.status() != DesignSystemKnowledgeSnapshot.Status.APPROVED) throw new IllegalStateException("승인된 Snapshot만 검색할 수 있습니다.");
        if (query == null || query.isBlank()) return List.of();
        String q = query.toLowerCase();
        return snapshot.references().stream().filter(ref -> (ref.artifactId()+" "+ref.artifactType()).toLowerCase().contains(q)).map(ref -> ref.artifactId()).sorted().toList();
    }
}
