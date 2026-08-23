package com.krdevops.springai.service.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpRegisteredToolCatalogTest {
    @Test void emptyProvider_hasDeterministicSnapshot() {
        var catalog = new McpRegisteredToolCatalog(ToolCallbackProvider.from());
        assertThat(catalog.toolNames()).isEmpty();
        assertThat(catalog.snapshotHash()).hasSize(64);
        assertThat(catalog.matchesSnapshot(catalog.snapshotHash())).isTrue();
        assertThat(catalog.matchesSnapshot("0".repeat(64))).isFalse();
        assertThat(catalog.matchesSnapshot("A".repeat(64))).isFalse();
        assertThat(catalog.matchesSnapshot("not-a-hash")).isFalse();
        assertThat(catalog.matchesSnapshot(null)).isFalse();
    }

    @Test void snapshotHash_changesWhenToolDescriptionOrSchemaChanges() {
        ToolCallback first = callback("same", "description-a", "{\"type\":\"object\"}");
        ToolCallback changed = callback("same", "description-b", "{\"type\":\"string\"}");

        String firstHash = new McpRegisteredToolCatalog(ToolCallbackProvider.from(first)).snapshotHash();
        String changedHash = new McpRegisteredToolCatalog(ToolCallbackProvider.from(changed)).snapshotHash();

        assertThat(changedHash).isNotEqualTo(firstHash);
    }

    @Test void snapshotHash_isIndependentOfRegistrationOrder() {
        ToolCallback a = callback("a", "a", "{}");
        ToolCallback b = callback("b", "b", "{}");

        String forward = new McpRegisteredToolCatalog(ToolCallbackProvider.from(a, b)).snapshotHash();
        String reverse = new McpRegisteredToolCatalog(ToolCallbackProvider.from(b, a)).snapshotHash();

        assertThat(forward).isEqualTo(reverse);
    }

    @Test void snapshotHash_normalizesInputSchemaObjectKeyOrder() {
        ToolCallback first = callback("schema", "d", "{\"type\":\"object\",\"properties\":{\"b\":{},\"a\":{}}}");
        ToolCallback reordered = callback("schema", "d", "{\"properties\":{\"a\":{},\"b\":{}},\"type\":\"object\"}");

        assertThat(new McpRegisteredToolCatalog(ToolCallbackProvider.from(first)).snapshotHash())
                .isEqualTo(new McpRegisteredToolCatalog(ToolCallbackProvider.from(reordered)).snapshotHash());
    }

    private ToolCallback callback(String name, String description, String schema) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name(name).description(description).inputSchema(schema).build());
        return callback;
    }
}
