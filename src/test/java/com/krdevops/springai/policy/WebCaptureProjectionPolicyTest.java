package com.krdevops.springai.policy;

import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebCaptureProjectionPolicyTest {

    @Test
    void excludesRawValuesAndSensitiveLabelsButKeepsBusinessLabel() {
        RenderedNode.Bounds bounds = new RenderedNode.Bounds(0, 0, 100, 30);
        List<RenderedNode> nodes = List.of(
                new RenderedNode("title", null, "ELEMENT", "input", "textbox",
                        "제목", null, "절대 노출 금지 입력값", true, bounds, Map.of(), List.of()),
                new RenderedNode("PASSWORD_HASH", null, "ELEMENT", "input", "textbox",
                        "PASSWORD_HASH", null, "secret-value", true, bounds, Map.of(), List.of()),
                new RenderedNode("profile", null, "ELEMENT", "div", "",
                        null, "홍길동 user@example.com 010-1234-5678", null, true, bounds, Map.of(), List.of()),
                new RenderedNode("save", null, "BUTTON", "button", "button",
                        null, "저장", null, true, bounds, Map.of(), List.of()));
        RenderedDesignDocument document = new RenderedDesignDocument(
                RenderedDesignDocument.SCHEMA_VERSION, "capture", "document", "a".repeat(64),
                new RenderedDesignDocument.Source("RENDERED_WEB_PAGE", "JSP", "http://localhost/",
                        "http://localhost/", "url", "2026-01-01T00:00:00Z"),
                new RenderedDesignDocument.Environment("desktop", 1440, 1200, 1,
                        "ko-KR", "Asia/Seoul", "light", true, "chromium"),
                new RenderedDesignDocument.Page("목록", "title", 1440, 1200, 0, 0, "white"),
                nodes, List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of());

        var projection = new WebCaptureProjectionPolicy().project(document);
        String serialized = projection.toString();

        assertThat(projection.fields()).extracting(field -> field.label()).containsExactly("제목");
        assertThat(projection.actions()).extracting(action -> action.label()).containsExactly("저장");
        assertThat(serialized).doesNotContain("secret-value", "절대 노출 금지 입력값",
                "홍길동", "user@example.com", "010-1234-5678", "PASSWORD_HASH");
    }
}
