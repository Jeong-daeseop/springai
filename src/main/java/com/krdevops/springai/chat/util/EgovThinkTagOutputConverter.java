package com.krdevops.springai.chat.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;

@Slf4j
public class EgovThinkTagOutputConverter<T> implements StructuredOutputConverter<T> {

    private final BeanOutputConverter<T> delegate;

    public EgovThinkTagOutputConverter(Class<T> targetClass) {
        this.delegate = new BeanOutputConverter<>(targetClass);
    }

    @Override
    public T convert(String text) {
        try {
            String cleanedJson = EgovResponseCleanerUtil.cleanResponse(text);
            log.debug("정리된 JSON: {}", cleanedJson);
            return delegate.convert(cleanedJson);
        } catch (Exception e) {
            log.error("JSON 변환 중 오류: {}", text, e);
            throw new RuntimeException("JSON 변환 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFormat() {
        return delegate.getFormat();
    }

    public static <T> EgovThinkTagOutputConverter<T> of(Class<T> targetClass) {
        return new EgovThinkTagOutputConverter<>(targetClass);
    }
}
