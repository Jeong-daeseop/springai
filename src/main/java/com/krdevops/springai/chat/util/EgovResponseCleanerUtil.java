package com.krdevops.springai.chat.util;

import java.util.regex.Pattern;

public class EgovResponseCleanerUtil {

    private static final Pattern THINK_TAG_PATTERN = Pattern.compile(
        "<think>.*?</think>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static String cleanResponse(String response) {
        if (response == null || response.trim().isEmpty()) return response;

        String cleaned = THINK_TAG_PATTERN.matcher(response.trim()).replaceAll("");

        int startBrace = cleaned.indexOf('{');
        int endBrace = cleaned.lastIndexOf('}');
        if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
            cleaned = cleaned.substring(startBrace, endBrace + 1);
        }
        return cleaned.trim();
    }
}
