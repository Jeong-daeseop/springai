package com.krdevops.springai.service.figma;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** R6-032: DB catalog 후보를 LLM으로 업무명과 매칭하는 제한된 구조화 resolver. */
@Service
public class NaturalLanguageTableResolver {
    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;

    public NaturalLanguageTableResolver(
            @Qualifier("openAiChatClient") ChatClient chatClient, JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Selection> resolve(String prompt, String database) {
        if (database == null || database.isBlank()) database = configuredDatabase();
        if (prompt == null || prompt.isBlank() || database == null || database.isBlank()) return Optional.empty();
        List<Candidate> candidates = jdbcTemplate.query(
                "SELECT TABLE_NAME, COALESCE(TABLE_COMMENT, '') AS TABLE_COMMENT "
                        + "FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? "
                        + "AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME",
                (rs, rowNum) -> new Candidate(rs.getString("TABLE_NAME"), rs.getString("TABLE_COMMENT")), database);
        if (candidates.isEmpty()) return Optional.empty();
        String catalog = candidates.stream().map(candidate -> candidate.tableName() + " | " + candidate.comment()).reduce("",
                (left, right) -> left.isBlank() ? right : left + "\n" + right);
        try {
            TableSelection selection = chatClient.prompt()
                    .user("업무 화면 요청과 DB 테이블 후보를 매칭하세요. 후보 밖의 테이블은 절대 선택하지 마세요.\n"
                            + "요청: " + prompt + "\nDB: " + database + "\n후보:\n" + catalog
                            + "\n반환 필드: tableName, confidence, reason")
                    .call().entity(TableSelection.class);
            if (selection == null || selection.tableName() == null) return Optional.empty();
            String selected = candidates.stream().map(Candidate::tableName)
                    .filter(name -> name.equalsIgnoreCase(selection.tableName().trim())).findFirst().orElse(null);
            if (selected == null || selection.confidence() < 0.60) return Optional.empty();
            return Optional.of(new Selection(database, selected, Math.min(1.0, Math.max(0.0, selection.confidence())),
                    selection.reason()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** 애플리케이션 DataSource의 catalog를 기본 업무 DB로 사용한다. */
    public Optional<Selection> resolve(String prompt) {
        return resolve(prompt, null);
    }

    private String configuredDatabase() {
        try (var connection = jdbcTemplate.getDataSource() == null
                ? null : jdbcTemplate.getDataSource().getConnection()) {
            return connection == null ? null : connection.getCatalog();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record Candidate(String tableName, String comment) {}

    public record Selection(String database, String tableName, double confidence, String reason) {}

    public record TableSelection(String tableName, double confidence, String reason) {}
}
