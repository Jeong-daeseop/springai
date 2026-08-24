package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 파일 내용에서 {@code @region:{type}:{id} start/end} 마커로 구분된 Region을 파싱한다. 감싸는
 * 주석 기호(// , &lt;!-- --&gt;, &lt;%-- --%&gt;)와 무관하게 마커 텍스트 자체만 인식하므로
 * Java/Thymeleaf HTML/JSP/MyBatis XML에 모두 그대로 쓸 수 있다.
 *
 * <p>마커 사이에 있지 않은 구간(예: import문, 마커가 아예 없는 파일의 일부)은 어떤 Region에도
 * 속하지 않는다 — New의 값을 그대로 유지한다는 점에서 암묵적으로 {@code GENERATED}와 동일하게
 * 취급된다.
 */
public final class RegionMarkerParser {

    private static final Pattern MARKER = Pattern.compile(
            "@region:([a-z]+):([A-Za-z0-9_.-]+)\\s+(start|end)");

    private RegionMarkerParser() {
    }

    public static List<ParsedRegion> parse(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<MarkerToken> tokens = findTokens(content);
        if (tokens.isEmpty()) {
            return List.of(new ParsedRegion("generated.file",
                    GenerationOwnershipManifest.RegionType.GENERATED, content, 0, content.length()));
        }
        List<ParsedRegion> regions = pairTokens(content, tokens);
        if (regions == null) {
            return List.of(new ParsedRegion("unknown.file",
                    GenerationOwnershipManifest.RegionType.UNKNOWN, content, 0, content.length()));
        }
        return regions;
    }

    public static String hashOf(String regionContent) {
        return ContentHashes.sha256Hex(regionContent.getBytes(StandardCharsets.UTF_8));
    }

    private static List<MarkerToken> findTokens(String content) {
        List<MarkerToken> tokens = new ArrayList<>();
        Matcher matcher = MARKER.matcher(content);
        while (matcher.find()) {
            tokens.add(new MarkerToken(matcher.group(1), matcher.group(2), matcher.group(3),
                    matcher.start(), matcher.end()));
        }
        return tokens;
    }

    /** 짝이 안 맞거나 id가 중복되면 {@code null}을 반환해 호출자가 파일 전체를 UNKNOWN으로 강등하게 한다. */
    private static List<ParsedRegion> pairTokens(String content, List<MarkerToken> tokens) {
        List<ParsedRegion> regions = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        MarkerToken pendingStart = null;
        for (MarkerToken token : tokens) {
            if ("start".equals(token.kind())) {
                if (pendingStart != null) {
                    return null;
                }
                pendingStart = token;
            } else {
                if (pendingStart == null || !pendingStart.id().equals(token.id())
                        || !pendingStart.type().equals(token.type())) {
                    return null;
                }
                if (!seenIds.add(pendingStart.id())) {
                    return null;
                }
                int interiorStart = pendingStart.markerEnd();
                int interiorEnd = findInteriorEnd(content, token.markerStart());
                regions.add(new ParsedRegion(pendingStart.id(), toRegionType(pendingStart.type()),
                        content.substring(interiorStart, interiorEnd), interiorStart, interiorEnd));
                pendingStart = null;
            }
        }
        return pendingStart != null ? null : regions;
    }

    private static int findInteriorEnd(String content, int markerStart) {
        // Scan backwards to see if the marker is on its own logical line (preceded only by whitespace)
        int i = markerStart - 1;
        while (i >= 0 && content.charAt(i) != '\n') {
            char c = content.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                // Found an identifier character, so the marker is on the same line as content
                return markerStart;
            }
            i--;
        }

        // If we found a newline (i >= 0), return the position after it
        // Otherwise, return markerStart
        return i >= 0 ? i + 1 : markerStart;
    }

    private static GenerationOwnershipManifest.RegionType toRegionType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "generated" -> GenerationOwnershipManifest.RegionType.GENERATED;
            case "binding" -> GenerationOwnershipManifest.RegionType.BINDING;
            case "protected" -> GenerationOwnershipManifest.RegionType.PROTECTED;
            default -> GenerationOwnershipManifest.RegionType.UNKNOWN;
        };
    }

    private record MarkerToken(String type, String id, String kind, int markerStart, int markerEnd) {
    }

    /** 파싱된 Region 1개. {@code startIndex}/{@code endIndex}는 원본 문자열에서 마커를 제외한
     * 내부 콘텐츠의 오프셋이라 스플라이스(내용 치환)에 그대로 쓸 수 있다. */
    public record ParsedRegion(String regionId, GenerationOwnershipManifest.RegionType regionType,
                                String content, int startIndex, int endIndex) {
    }
}
