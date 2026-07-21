package com.krdevops.springai.service;

import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** 프로그램 테이블 URL에서 Controller path와 기본 bbsId를 안전하게 분리한다. */
@Service
public class BoardProgramUrlParser {

    public ParsedBoardUrl parse(String url) {
        if (url == null || url.isBlank()) return new ParsedBoardUrl(null, null, null);
        String original = url.trim();
        int fragment = original.indexOf('#');
        String withoutFragment = fragment >= 0 ? original.substring(0, fragment) : original;
        int question = withoutFragment.indexOf('?');
        String path = question >= 0 ? withoutFragment.substring(0, question) : withoutFragment;
        String query = question >= 0 ? withoutFragment.substring(question + 1) : null;
        if (path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException("게시판 프로그램 URL path가 올바르지 않습니다: " + original);
        }

        String bbsId = null;
        if (query != null && !query.isBlank()) {
            for (String pair : query.split("&", -1)) {
                int equals = pair.indexOf('=');
                String key = decode(equals >= 0 ? pair.substring(0, equals) : pair, original);
                if (!"bbsId".equalsIgnoreCase(key)) continue;
                String value = decode(equals >= 0 ? pair.substring(equals + 1) : "", original);
                if (bbsId != null && !bbsId.equals(value)) {
                    throw new IllegalArgumentException("bbsId가 중복된 프로그램 URL입니다: " + original);
                }
                bbsId = value.isBlank() ? null : value;
            }
        }
        return new ParsedBoardUrl(original, path, bbsId);
    }

    private String decode(String value, String original) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL 인코딩이 올바르지 않습니다: " + original, e);
        }
    }

    public record ParsedBoardUrl(String originalUrl, String path, String bbsId) {}
}
