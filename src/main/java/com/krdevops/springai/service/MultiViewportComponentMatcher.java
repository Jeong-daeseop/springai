package com.krdevops.springai.service;

import com.krdevops.springai.model.capture.BreakpointObservation;
import com.krdevops.springai.model.capture.ComponentMatch;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * R8(04번 문서 §11): {@code selectorHint}(R2-05) 일치를 기준으로 하는 공통 component matcher.
 * 임의 픽셀 임계값 대신 "존재 여부"와 "부모 selectorHint 일치 여부"라는 두 가지 확실한 근거만
 * 사용한다 — viewport마다 폭이 달라 절대좌표가 자연스럽게 달라지는 것과, 실제로 DOM이
 * 재배치(reparent)된 것을 구분하기 위함이다.
 */
public final class MultiViewportComponentMatcher {

    private static final List<String> VIEWPORT_ORDER = List.of("desktop", "tablet", "mobile");

    private MultiViewportComponentMatcher() {
    }

    public record Result(List<ComponentMatch> componentMatches, List<BreakpointObservation> breakpointObservations) {
    }

    public static Result analyze(Map<String, RenderedDesignDocument> documentsByViewport) {
        List<String> presentViewports = VIEWPORT_ORDER.stream()
                .filter(documentsByViewport::containsKey).toList();

        Map<String, Map<String, RenderedNode>> nodesById = new LinkedHashMap<>();
        Map<String, Map<String, RenderedNode>> firstBySelectorHint = new LinkedHashMap<>();
        for (String viewport : presentViewports) {
            Map<String, RenderedNode> byId = new LinkedHashMap<>();
            Map<String, RenderedNode> byHint = new LinkedHashMap<>();
            for (RenderedNode node : documentsByViewport.get(viewport).nodes()) {
                byId.put(node.id(), node);
                if (node.selectorHint() != null) byHint.putIfAbsent(node.selectorHint(), node);
            }
            nodesById.put(viewport, byId);
            firstBySelectorHint.put(viewport, byHint);
        }

        Set<String> allHints = new LinkedHashSet<>();
        firstBySelectorHint.values().forEach(byHint -> allHints.addAll(byHint.keySet()));

        List<ComponentMatch> matches = new ArrayList<>();
        List<BreakpointObservation> observations = new ArrayList<>();
        for (String hint : allHints) {
            Map<String, String> nodeIdsByViewport = new LinkedHashMap<>();
            Map<String, String> parentHintByViewport = new LinkedHashMap<>();
            for (String viewport : presentViewports) {
                RenderedNode node = firstBySelectorHint.get(viewport).get(hint);
                if (node == null) continue;
                nodeIdsByViewport.put(viewport, node.id());
                parentHintByViewport.put(viewport, parentSelectorHint(nodesById.get(viewport), node));
            }

            boolean presentInAll = nodeIdsByViewport.size() == presentViewports.size();
            boolean parentsConsistent = new LinkedHashSet<>(parentHintByViewport.values()).size() <= 1;
            ComponentMatch.Status status = !presentInAll ? ComponentMatch.Status.HIDDEN_IN_SOME
                    : parentsConsistent ? ComponentMatch.Status.MATCHED_ALL : ComponentMatch.Status.MOVED;
            matches.add(new ComponentMatch(hint, nodeIdsByViewport, status));

            for (int i = 0; i < presentViewports.size() - 1; i++) {
                String from = presentViewports.get(i);
                String to = presentViewports.get(i + 1);
                boolean inFrom = nodeIdsByViewport.containsKey(from);
                boolean inTo = nodeIdsByViewport.containsKey(to);
                if (inFrom && !inTo) {
                    observations.add(new BreakpointObservation(hint, from, to, BreakpointObservation.Change.HIDDEN));
                } else if (!inFrom && inTo) {
                    observations.add(new BreakpointObservation(hint, from, to, BreakpointObservation.Change.SHOWN));
                } else if (inFrom && !Objects.equals(parentHintByViewport.get(from), parentHintByViewport.get(to))) {
                    observations.add(new BreakpointObservation(hint, from, to, BreakpointObservation.Change.MOVED));
                }
            }
        }
        return new Result(matches, observations);
    }

    private static String parentSelectorHint(Map<String, RenderedNode> nodesById, RenderedNode node) {
        if (node.parentId() == null) return null;
        RenderedNode parent = nodesById.get(node.parentId());
        return parent == null ? null : parent.selectorHint();
    }
}
