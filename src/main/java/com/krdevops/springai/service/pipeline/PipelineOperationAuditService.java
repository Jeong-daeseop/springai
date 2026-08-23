package com.krdevops.springai.service.pipeline;

import com.krdevops.springai.model.artifact.ContentHashes;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 작업 관문 통과/거부 이력을 handoff evidence에 연결할 수 있도록 보관한다. */
@Service
public class PipelineOperationAuditService {
    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final int maxEntries;

    public PipelineOperationAuditService() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public PipelineOperationAuditService(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries는 1 이상이어야 합니다.");
        this.maxEntries = maxEntries;
    }

    public Entry record(String operation, String risk, boolean authorized) {
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation은 필수입니다.");
        if (risk == null || risk.isBlank()) throw new IllegalArgumentException("risk는 필수입니다.");
        Entry entry = new Entry(operation, risk, authorized, Instant.now());
        entries.add(entry);
        while (entries.size() > maxEntries) entries.remove(0);
        return entry;
    }

    public List<Entry> all() { return List.copyOf(entries); }

    public List<Entry> findByOperation(String operation) {
        if (operation == null || operation.isBlank()) return List.of();
        return entries.stream().filter(entry -> entry.operation().equals(operation)).toList();
    }

    public List<Entry> recent(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        int from = Math.max(0, entries.size() - limit);
        return List.copyOf(entries.subList(from, entries.size()));
    }

    public long countAuthorized() { return entries.stream().filter(Entry::authorized).count(); }
    public long countDenied() { return entries.stream().filter(entry -> !entry.authorized()).count(); }
    public long countByRisk(String risk) { return risk == null ? 0 : entries.stream().filter(entry -> risk.equals(entry.risk())).count(); }
    public long countByOperation(String operation) { return operation == null ? 0 : entries.stream().filter(entry -> operation.equals(entry.operation())).count(); }
    public long countAuthorizedByOperation(String operation) { return operation == null ? 0 : entries.stream().filter(entry -> operation.equals(entry.operation()) && entry.authorized()).count(); }
    public long countDeniedByOperation(String operation) { return operation == null ? 0 : entries.stream().filter(entry -> operation.equals(entry.operation()) && !entry.authorized()).count(); }

    /** 현재 감사 이력을 evidence bundle에 참조할 수 있는 결정론적 지문으로 만든다. */
    public String snapshotHash() {
        String canonical = entries.stream()
                .map(entry -> entry.operation() + "|" + entry.risk() + "|"
                        + entry.authorized() + "|" + entry.occurredAt())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return ContentHashes.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public record Entry(String operation, String risk, boolean authorized, Instant occurredAt) { }
}
