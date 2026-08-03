package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ARCH-0405 기본 구현: WP4 이전 {@code ConcurrentHashMap} 동작을 그대로 보존한 in-memory
 * Store. 재시작하면 사라지므로 운영에는 쓰지 않는다 — 순수 단위 테스트와, DB 없이 Workflow
 * 로직만 검증하고 싶은 임베디드 사용을 위한 기본값이다(RISK-03 자체는 MySQL 구현인
 * {@code ThymeleafProjectOperationRepository}가 해결한다).
 *
 * <p>{@link ConcurrentHashMap#compute}가 key 단위로 원자적이라는 성질을 이용해 compare-and-set을
 * 구현한다 — DB 구현의 {@code PRIMARY KEY(OPERATION_ID, REVISION)} 충돌 검출과 같은 보장을
 * in-memory에서도 재현한다.
 */
public class InMemoryThymeleafOperationStore implements ThymeleafOperationStore {

    private final ConcurrentHashMap<String, ThymeleafOperationSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> previewHashIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<ThymeleafOperationSnapshot> findLatest(String operationId) {
        return Optional.ofNullable(snapshots.get(operationId));
    }

    @Override
    public ThymeleafOperationSnapshot createOrReuse(ThymeleafOperationSnapshot initial) {
        if (initial.revision() != 1) {
            throw new IllegalArgumentException("createOrReuse는 revision=1만 허용합니다: " + initial.revision());
        }
        String winnerOperationId = previewHashIndex.putIfAbsent(initial.previewHash(), initial.operationId());
        if (winnerOperationId != null) {
            return findLatest(winnerOperationId).orElseThrow(() -> new IllegalStateException(
                    "THYMELEAF_OPERATION_IDEMPOTENCY_INDEX_CORRUPT: " + initial.previewHash()));
        }
        return save(initial);
    }

    @Override
    public ThymeleafOperationSnapshot save(ThymeleafOperationSnapshot snapshot) {
        AtomicReference<ThymeleafOperationSnapshot> saved = new AtomicReference<>();
        AtomicBoolean conflict = new AtomicBoolean(false);

        snapshots.compute(snapshot.operationId(), (id, current) -> {
            int expectedPreviousRevision = snapshot.revision() - 1;
            int currentRevision = current == null ? 0 : current.revision();
            if (currentRevision != expectedPreviousRevision) {
                conflict.set(true);
                return current;
            }
            saved.set(snapshot);
            return snapshot;
        });

        if (conflict.get()) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_REVISION_CONFLICT: " + snapshot.operationId() + "/" + snapshot.revision());
        }
        return saved.get();
    }
}
