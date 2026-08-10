package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ARCH-0409/0410/0411/0418/0419/0420: {@link ThymeleafProjectOperationRepository}가
 * revision 기반 compare-and-set, previewHash 멱등성, 재시작 후 복구를 실제로 제공하는지
 * 실 MySQL로 검증한다.
 */
class ThymeleafProjectOperationRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ThymeleafProjectOperationRepository newRepository() {
        return new ThymeleafProjectOperationRepository(jdbcTemplate, objectMapper);
    }

    private ThymeleafOperationSnapshot snapshot(int revision, String operationId,
            ProjectOperationStatus status, String previewHash) {
        ThymeleafProjectOperation operation = new ThymeleafProjectOperation(
                operationId, "/tmp/project", status,
                Map.of("list.html", "<html></html>"), List.of("list.html"), null,
                List.of(), List.of(), true, LocalDateTime.now(), null);
        return new ThymeleafOperationSnapshot(
                revision, operation, "/tmp/project", Map.of("list.html", "hash-1"),
                "design-rev-1", previewHash);
    }

    @Test
    void restartRecovery_newRepositoryInstanceSeesPreviouslyPersistedRevisions() {
        // "재시작"을 새 Repository 인스턴스 생성으로 시뮬레이션한다 — 상태는 인스턴스가 아니라
        // DB에 있어야 하므로, 새 인스턴스도 이전 인스턴스가 저장한 revision을 그대로 봐야 한다.
        String operationId = "thymop-restart-" + UUID.randomUUID();
        ThymeleafProjectOperationRepository beforeRestart = newRepository();
        ThymeleafOperationSnapshot rev1 = snapshot(1, operationId, ProjectOperationStatus.PREVIEW_READY, "hash-" + operationId);
        beforeRestart.createOrReuse(rev1);
        ThymeleafOperationSnapshot rev2 = snapshot(2, operationId, ProjectOperationStatus.APPROVED, rev1.previewHash());
        beforeRestart.save(rev2);

        ThymeleafProjectOperationRepository afterRestart = newRepository();
        ThymeleafOperationSnapshot recovered = afterRestart.findLatest(operationId).orElseThrow();

        assertThat(recovered.revision()).isEqualTo(2);
        assertThat(recovered.operation().status()).isEqualTo(ProjectOperationStatus.APPROVED);
    }

    @Test
    void concurrentRevisionSave_onlyOneWinner() throws Exception {
        String operationId = "thymop-race-" + UUID.randomUUID();
        ThymeleafProjectOperationRepository repository = newRepository();
        repository.createOrReuse(snapshot(1, operationId, ProjectOperationStatus.PREVIEW_READY, "hash-" + operationId));

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        try {
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(5, TimeUnit.SECONDS);
                        repository.save(snapshot(2, operationId, ProjectOperationStatus.APPROVED, "hash-" + operationId));
                        successCount.incrementAndGet();
                    } catch (IllegalStateException conflict) {
                        conflictCount.incrementAndGet();
                    } catch (Exception unexpected) {
                        throw new RuntimeException(unexpected);
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        // ARCH-0419: 동시에 같은 다음 revision을 쓰려는 시도 중 정확히 하나만 성공해야 한다.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    void duplicatePreviewHash_reusesSameOperationInsteadOfCreatingNew() {
        String previewHash = "shared-hash-" + UUID.randomUUID();
        ThymeleafProjectOperationRepository repository = newRepository();

        ThymeleafOperationSnapshot first = repository.createOrReuse(
                snapshot(1, "thymop-a-" + UUID.randomUUID(), ProjectOperationStatus.PREVIEW_READY, previewHash));
        ThymeleafOperationSnapshot second = repository.createOrReuse(
                snapshot(1, "thymop-b-" + UUID.randomUUID(), ProjectOperationStatus.PREVIEW_READY, previewHash));

        assertThat(second.operationId()).isEqualTo(first.operationId());
    }

    @Test
    void savingSameRevisionTwice_isRejectedAsConflict() {
        String operationId = "thymop-dup-" + UUID.randomUUID();
        ThymeleafProjectOperationRepository repository = newRepository();
        repository.createOrReuse(snapshot(1, operationId, ProjectOperationStatus.PREVIEW_READY, "hash-" + operationId));

        assertThatThrownBy(() -> repository.save(
                snapshot(1, operationId, ProjectOperationStatus.PREVIEW_READY, "hash-" + operationId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("THYMELEAF_OPERATION_REVISION_CONFLICT");
    }

    @Test
    void indexScreenOperation_findLatestByScreen_roundTripsThroughRealMysql() {
        String projectRootHash = "hash-" + UUID.randomUUID();
        String screenId = "employer-list";
        String operationId = "thymop-screen-" + UUID.randomUUID();
        ThymeleafProjectOperationRepository repository = newRepository();
        repository.createOrReuse(
                snapshot(1, operationId, ProjectOperationStatus.APPLIED, "hash-" + operationId));

        assertThat(repository.findLatestByScreen(projectRootHash, screenId)).isEmpty();

        repository.indexScreenOperation(projectRootHash, screenId, operationId);

        assertThat(repository.findLatestByScreen(projectRootHash, screenId))
                .map(ThymeleafOperationSnapshot::operationId)
                .contains(operationId);
    }

    @Test
    void indexScreenOperation_reindexingSameScreen_overwritesPreviousOperationId() {
        String projectRootHash = "hash-" + UUID.randomUUID();
        String screenId = "employer-list";
        ThymeleafProjectOperationRepository repository = newRepository();
        String firstOperationId = "thymop-screen-first-" + UUID.randomUUID();
        String secondOperationId = "thymop-screen-second-" + UUID.randomUUID();
        repository.createOrReuse(
                snapshot(1, firstOperationId, ProjectOperationStatus.APPLIED, "hash-" + firstOperationId));
        repository.createOrReuse(
                snapshot(1, secondOperationId, ProjectOperationStatus.APPLIED, "hash-" + secondOperationId));

        repository.indexScreenOperation(projectRootHash, screenId, firstOperationId);
        repository.indexScreenOperation(projectRootHash, screenId, secondOperationId);

        assertThat(repository.findLatestByScreen(projectRootHash, screenId))
                .map(ThymeleafOperationSnapshot::operationId)
                .contains(secondOperationId);
    }
}
