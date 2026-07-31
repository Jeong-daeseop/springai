package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;

import java.util.List;

/**
 * 기존 협력자가 {@code List<String> failed}에 직접 누적하는 방식(예:
 * {@code ThymeleafRuntimeConfigurer})을 {@link ProcessorResult}로 옮기는 변환 도우미.
 * 메시지 문자열은 가공하지 않고 그대로 옮긴다.
 */
final class ProcessorFailures {

    private ProcessorFailures() {
    }

    static ProcessorResult toResult(String source, List<String> failed) {
        if (failed.isEmpty()) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed(
                failed.stream().map(message -> new GenerationFailure(source, message)).toList());
    }
}
