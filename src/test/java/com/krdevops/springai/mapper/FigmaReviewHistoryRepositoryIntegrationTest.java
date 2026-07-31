package com.krdevops.springai.mapper;

import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** R1-T04/T05: FigmaReviewHistoryRepository 저장·조회와 스키마 초기화 반복 실행 안전성. */
class FigmaReviewHistoryRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final FigmaReviewHistoryRepository repository = new FigmaReviewHistoryRepository(jdbcTemplate);

    @Test
    void createTableIfNotExistsIsIdempotent() {
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
    }

    @Test
    void findByTargetReturnsEventsInOccurredOrder() {
        repository.createTableIfNotExists();
        String targetId = "test-" + UUID.randomUUID();
        try {
            repository.save(event(targetId, FigmaReviewEvent.EventType.REVIEW, LocalDateTime.now().minusMinutes(1)));
            repository.save(event(targetId, FigmaReviewEvent.EventType.APPROVAL, LocalDateTime.now()));

            List<FigmaReviewEvent> events = repository.findByTarget(
                    FigmaReviewEvent.TargetType.DESIGN_SYSTEM_PROFILE, targetId, "1.0");

            assertThat(events).extracting(FigmaReviewEvent::eventType)
                    .containsExactly(FigmaReviewEvent.EventType.REVIEW, FigmaReviewEvent.EventType.APPROVAL);
        } finally {
            jdbcTemplate.update("DELETE FROM AI_FIGMA_REVIEW_HISTORY WHERE TARGET_ID = ?", targetId);
        }
    }

    private FigmaReviewEvent event(String targetId, FigmaReviewEvent.EventType type, LocalDateTime occurredAt) {
        return new FigmaReviewEvent(
                UUID.randomUUID().toString(), FigmaReviewEvent.TargetType.DESIGN_SYSTEM_PROFILE,
                targetId, "1.0", type, "OK", "tester", null, occurredAt);
    }
}
