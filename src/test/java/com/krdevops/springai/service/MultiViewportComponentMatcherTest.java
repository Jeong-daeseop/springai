package com.krdevops.springai.service;

import com.krdevops.springai.model.capture.BreakpointObservation;
import com.krdevops.springai.model.capture.ComponentMatch;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** R8(04번 문서 §11): selectorHint 기반 컴포넌트 대응 알고리즘의 핵심 3분류(MATCHED_ALL/HIDDEN_IN_SOME/MOVED)를 검증한다. */
class MultiViewportComponentMatcherTest {

    @Test
    void matchedAllWhenPresentEverywhereWithSameParentSelectorHint() {
        Map<String, RenderedDesignDocument> documents = new LinkedHashMap<>();
        documents.put("desktop", document(
                node("root", null, null), node("gnb", "root", "nav#gnb")));
        documents.put("tablet", document(
                node("root", null, null), node("gnb", "root", "nav#gnb")));
        documents.put("mobile", document(
                node("root", null, null), node("gnb", "root", "nav#gnb")));

        MultiViewportComponentMatcher.Result result = MultiViewportComponentMatcher.analyze(documents);

        ComponentMatch gnbMatch = onlyMatch(result, "nav#gnb");
        assertThat(gnbMatch.status()).isEqualTo(ComponentMatch.Status.MATCHED_ALL);
        assertThat(gnbMatch.nodeIdsByViewport()).containsOnlyKeys("desktop", "tablet", "mobile");
        assertThat(result.breakpointObservations()).noneMatch(o -> o.selectorHint().equals("nav#gnb"));
    }

    @Test
    void hiddenInSomeWhenMissingFromAViewportAndShownObservationOnAppearance() {
        Map<String, RenderedDesignDocument> documents = new LinkedHashMap<>();
        documents.put("desktop", document(node("root", null, null)));
        documents.put("tablet", document(node("root", null, null)));
        documents.put("mobile", document(
                node("root", null, null), node("hamburger", "root", "button.hamburger")));

        MultiViewportComponentMatcher.Result result = MultiViewportComponentMatcher.analyze(documents);

        ComponentMatch match = onlyMatch(result, "button.hamburger");
        assertThat(match.status()).isEqualTo(ComponentMatch.Status.HIDDEN_IN_SOME);
        assertThat(match.nodeIdsByViewport()).containsOnlyKeys("mobile");
        assertThat(result.breakpointObservations()).contains(
                new BreakpointObservation("button.hamburger", "tablet", "mobile", BreakpointObservation.Change.SHOWN));
        assertThat(result.breakpointObservations()).noneMatch(o ->
                o.selectorHint().equals("button.hamburger") && o.fromViewport().equals("desktop"));
    }

    @Test
    void movedOnlyBetweenTheAdjacentPairWhoseParentSelectorHintActuallyDiffers() {
        Map<String, RenderedDesignDocument> documents = new LinkedHashMap<>();
        documents.put("desktop", document(
                node("root", null, null), node("header", "root", "header#top"),
                node("search", "header", "div.search-panel")));
        documents.put("tablet", document(
                node("root", null, null), node("header", "root", "header#top"),
                node("search", "header", "div.search-panel")));
        documents.put("mobile", document(
                node("root", null, null), node("menu", "root", "nav.mobile-menu"),
                node("search", "menu", "div.search-panel")));

        MultiViewportComponentMatcher.Result result = MultiViewportComponentMatcher.analyze(documents);

        ComponentMatch match = onlyMatch(result, "div.search-panel");
        assertThat(match.status()).isEqualTo(ComponentMatch.Status.MOVED);
        assertThat(result.breakpointObservations()).contains(
                new BreakpointObservation("div.search-panel", "tablet", "mobile", BreakpointObservation.Change.MOVED));
        assertThat(result.breakpointObservations()).noneMatch(o ->
                o.selectorHint().equals("div.search-panel") && o.fromViewport().equals("desktop"));
    }

    private static ComponentMatch onlyMatch(MultiViewportComponentMatcher.Result result, String selectorHint) {
        return result.componentMatches().stream()
                .filter(m -> m.selectorHint().equals(selectorHint))
                .findFirst().orElseThrow();
    }

    private static RenderedNode node(String id, String parentId, String selectorHint) {
        var bounds = new RenderedNode.Bounds(0, 0, 10, 10);
        return new RenderedNode(id, parentId, "ELEMENT", "div", null, null, null, null,
                true, bounds, Map.of(), List.of(), selectorHint, 0, 0.0, null);
    }

    private static RenderedDesignDocument document(RenderedNode... nodes) {
        return new RenderedDesignDocument(RenderedDesignDocument.SCHEMA_VERSION,
                "11111111-1111-4111-8111-111111111111", "b".repeat(64), "a".repeat(64),
                new RenderedDesignDocument.Source("RENDERED_WEB_PAGE", "UNKNOWN",
                        "http://localhost:8080/page.do", "http://localhost:8080/page.do",
                        "c".repeat(64), "2026-08-20T00:00:00Z"),
                new RenderedDesignDocument.Environment("desktop", 1440, 1200, 1, "ko-KR",
                        "Asia/Seoul", "light", true, "chromium"),
                new RenderedDesignDocument.Page("test", "root", 1440, 1200, 0, 0, "white"),
                List.of(nodes), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of());
    }
}
