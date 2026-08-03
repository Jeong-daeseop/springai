package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;

import java.util.Optional;

/**
 * ARCH-0405: 공통 {@code OperationRepositoryPort}의 Thymeleaf 전용 계약.
 *
 * <p>구현체는 {@code InMemoryThymeleafOperationStore}(테스트·임베디드 기본값,
 * ARCH-WP4 이전 {@code ConcurrentHashMap} 동작을 그대로 보존)와
 * {@code ThymeleafProjectOperationRepository}(MySQL, 운영 기본값) 두 종류다.
 */
public interface ThymeleafOperationStore {

    Optional<ThymeleafOperationSnapshot> findLatest(String operationId);

    /**
     * {@code initial.previewHash()}로 이미 생성된 Operation이 있으면 새로 만들지 않고 그것을
     * 원자적으로 반환한다(ARCH-0411 canonical hash 멱등성 index). {@code initial.revision()}은
     * 항상 1이어야 한다.
     */
    ThymeleafOperationSnapshot createOrReuse(ThymeleafOperationSnapshot initial);

    /**
     * {@code snapshot.revision()}이 이 저장소가 가진 해당 operationId의 현재 최신 revision + 1이
     * 아니면 거부한다(compare-and-set) — 동시에 같은 Operation을 전이시키려는 두 호출 중
     * 하나만 성공해야 한다(ARCH-0419).
     *
     * @throws IllegalStateException revision 충돌 시 {@code THYMELEAF_OPERATION_REVISION_CONFLICT}
     */
    ThymeleafOperationSnapshot save(ThymeleafOperationSnapshot snapshot);
}
