package com.krdevops.springai.model.design;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Figma nodeId 표기를 한 곳에서 정규화한다. URL은 {@code 1-2}, REST API는 {@code 1:2}를 쓰므로
 * 두 표기를 받아 항상 {@code 1:2}로 맞춘다.
 *
 * <p>URL 파싱({@code FigmaReferenceValidator})과 요청 생성 경로가 서로 다른 규칙을 쓰면 정규화되지
 * 않은 nodeId가 그대로 저장돼 Apply 시점 scope 재검증에서 "존재하지 않는 노드"로 오판되고, CONFLICT는
 * 종단 상태라 해당 요청이 영구히 복구 불가가 된다. 그래서 규칙을 여기 하나로 모은다.
 */
public final class FigmaNodeIds {

    private static final Pattern NODE_ID_PATTERN = Pattern.compile("[0-9]+[:-][0-9]+");

    private FigmaNodeIds() {
    }

    /** 비어 있으면 {@code null}, 형식이 어긋나면 예외. 그 외에는 {@code 1:2} 형태로 정규화한다. */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String decoded = URLDecoder.decode(value.trim(), StandardCharsets.UTF_8);
        if (!NODE_ID_PATTERN.matcher(decoded).matches()) {
            throw new IllegalArgumentException("올바른 Figma nodeId 형식이 아닙니다: " + decoded);
        }
        return decoded.replace('-', ':');
    }

    /** 목록 전체를 정규화한다. 빈 값이 섞여 있으면 어느 위치인지 알려주고 거부한다. */
    public static List<String> normalizeAll(List<String> values, String fieldName) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(value -> {
                    String normalized = normalize(value);
                    if (normalized == null) {
                        throw new IllegalArgumentException(fieldName + "에 빈 nodeId가 있습니다.");
                    }
                    return normalized;
                })
                .toList();
    }
}
