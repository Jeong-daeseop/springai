package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementConflictStatus;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementOwner;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatch;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatchSet;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPropertyType;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementScope;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MR-T07: FigmaRefinementRepository 불변·멱등 저장, 상태 전이 낙관적 잠금, 조회 통합 검증. */
class FigmaRefinementRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final FigmaRefinementRepository repository = new FigmaRefinementRepository(
            jdbcTemplate, new ObjectMapper(), new LegacyRepositoryDdlProperties());

    @Test
    void createTableIfNotExistsIsIdempotent() {
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
    }

    @Test
    void savingSameIdWithSameContentIsIdempotent() {
        repository.createTableIfNotExists();
        String patchSetId = "test-refine-" + UUID.randomUUID();
        try {
            FigmaRefinementPatchSet candidate = patchSet(patchSetId);
            repository.saveImmutable(candidate);
            assertThatCode(() -> repository.saveImmutable(candidate)).doesNotThrowAnyException();

            Optional<FigmaRefinementPatchSet> found = repository.findById(patchSetId);
            assertThat(found).isPresent();
            assertThat(found.get().patches()).hasSize(1);
        } finally {
            cleanup(patchSetId);
        }
    }

    @Test
    void savingSameIdWithDifferentContentIsRejected() {
        repository.createTableIfNotExists();
        String patchSetId = "test-refine-" + UUID.randomUUID();
        try {
            repository.saveImmutable(patchSet(patchSetId));
            FigmaRefinementPatchSet different = new FigmaRefinementPatchSet(
                    patchSetId, "qna-detail", 4, "fnv1a32:different:1", FigmaRefinementStatus.CAPTURED,
                    LocalDateTime.now(), null, null, null, List.of());

            assertThatThrownBy(() -> repository.saveImmutable(different))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REFINEMENT_PATCH_SET_CONFLICT");
        } finally {
            cleanup(patchSetId);
        }
    }

    @Test
    void transitionRequiresExpectedStatusAndIsRejectedOnMismatch() {
        repository.createTableIfNotExists();
        String patchSetId = "test-refine-" + UUID.randomUUID();
        try {
            repository.saveImmutable(patchSet(patchSetId));

            FigmaRefinementPatchSet transitioned = repository.transition(
                    patchSetId, FigmaRefinementStatus.CAPTURED, FigmaRefinementStatus.REVIEW_REQUIRED, null, null);
            assertThat(transitioned.status()).isEqualTo(FigmaRefinementStatus.REVIEW_REQUIRED);

            assertThatThrownBy(() -> repository.transition(
                    patchSetId, FigmaRefinementStatus.CAPTURED, FigmaRefinementStatus.APPROVED, "actor", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REFINEMENT_INVALID_TRANSITION");

            FigmaRefinementPatchSet approved = repository.transition(
                    patchSetId, FigmaRefinementStatus.REVIEW_REQUIRED, FigmaRefinementStatus.APPROVED,
                    "operator-1", "승인합니다");
            assertThat(approved.status()).isEqualTo(FigmaRefinementStatus.APPROVED);
            assertThat(approved.approvedBy()).isEqualTo("operator-1");
            assertThat(approved.approvedAt()).isNotNull();
        } finally {
            cleanup(patchSetId);
        }
    }

    @Test
    void findLatestApprovedByScreenReturnsOnlyApprovedOrAppliedPatchSets() {
        repository.createTableIfNotExists();
        String draftId = "test-refine-" + UUID.randomUUID();
        String approvedId = "test-refine-" + UUID.randomUUID();
        String screenId = "qna-detail-" + UUID.randomUUID();
        try {
            repository.saveImmutable(patchSetForScreen(draftId, screenId));
            repository.saveImmutable(patchSetForScreen(approvedId, screenId));
            repository.transition(approvedId, FigmaRefinementStatus.CAPTURED, FigmaRefinementStatus.REVIEW_REQUIRED, null, null);
            repository.transition(approvedId, FigmaRefinementStatus.REVIEW_REQUIRED, FigmaRefinementStatus.APPROVED, "operator-1", null);

            Optional<FigmaRefinementPatchSet> latest = repository.findLatestApprovedByScreen(screenId);

            assertThat(latest).isPresent();
            assertThat(latest.get().patchSetId()).isEqualTo(approvedId);
        } finally {
            cleanup(draftId);
            cleanup(approvedId);
        }
    }

    private void cleanup(String patchSetId) {
        jdbcTemplate.update("DELETE FROM AI_FIGMA_REFINEMENT_PATCH WHERE PATCH_SET_ID = ?", patchSetId);
        jdbcTemplate.update("DELETE FROM AI_FIGMA_REFINEMENT_SET WHERE PATCH_SET_ID = ?", patchSetId);
    }

    private FigmaRefinementPatchSet patchSet(String patchSetId) {
        return patchSetForScreen(patchSetId, "qna-detail");
    }

    private FigmaRefinementPatchSet patchSetForScreen(String patchSetId, String screenId) {
        FigmaRefinementPatch patch = new FigmaRefinementPatch(
                screenId + "/detail/contact", "krds.detailRow", "width", FigmaRefinementPropertyType.NUMBER,
                160, 176, FigmaRefinementOwner.MANUAL_REFINEMENT, FigmaRefinementScope.CONDITIONAL,
                FigmaRefinementConflictStatus.NONE);
        return new FigmaRefinementPatchSet(
                patchSetId, screenId, 3, "fnv1a32:abc123:512", FigmaRefinementStatus.CAPTURED,
                LocalDateTime.now(), null, null, null, List.of(patch));
    }
}
