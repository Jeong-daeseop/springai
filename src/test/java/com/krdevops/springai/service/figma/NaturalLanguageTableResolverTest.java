package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NaturalLanguageTableResolverTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void acceptsOnlyHighConfidenceTableFromCatalog() throws Exception {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(NaturalLanguageTableResolver.TableSelection.class)).thenReturn(
                new NaturalLanguageTableResolver.TableSelection("LETTNEMPLYRINFO", .92, "직원 목록 의미 일치"));

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("TABLE_NAME")).thenReturn("LETTNEMPLYRINFO");
        when(row.getString("TABLE_COMMENT")).thenReturn("직원 정보");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("ebt")))
                .thenAnswer(invocation -> List.of(((RowMapper) invocation.getArgument(1)).mapRow(row, 0)));

        // JdbcTemplate RowMapper는 실제 DB에서만 호출되므로 후보 목록은 query 결과를 직접 공급한다.
        var resolver = new NaturalLanguageTableResolver(chatClient, jdbcTemplate);
        assertThat(resolver.resolve("직원 목록", "ebt")).get()
                .extracting(NaturalLanguageTableResolver.Selection::tableName)
                .isEqualTo("LETTNEMPLYRINFO");
    }
}
