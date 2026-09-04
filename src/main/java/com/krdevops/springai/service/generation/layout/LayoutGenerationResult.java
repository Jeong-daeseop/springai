package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;

import java.nio.file.Path;
import java.util.List;

/**
 * {@link ThymeleafLayoutGenerationPlanner}/{@link ThymeleafLayoutGenerationService}가 조립한
 * 구조화된 생성 결과 — {@code service.generation.mcp.ThymeleafLayoutResultFormatter}만이
 * 이 값으로부터 최종 MCP 응답 문자열을 만든다.
 */
public record LayoutGenerationResult(
        String outputPath,
        String resolvedBasePath,
        String resolvedPackageName,
        String resolvedMenuTableName,
        String resolvedProgramTableName,
        List<FileOutcome> layoutFileOutcomes,
        String logoResultLine,
        List<FileOutcome> gnbComponentOutcomes,
        FileOutcome mainHtmlOutcome,
        ThymeleafLayoutValidator.LayoutValidationResult validation,
        String servletContextPatchMessage,
        MyBatisRuntimeConfigurer.ConfigurationResult myBatisResult,
        String egovVersion,
        boolean runtimeSkipped,
        List<String> runtimeFailures,
        String projectType) {

    public enum Status { CREATED, PRESERVED, FAILED }

    public record FileOutcome(Path path, Status status, String detail) {
        public static FileOutcome created(Path path) {
            return new FileOutcome(path, Status.CREATED, null);
        }

        public static FileOutcome preserved(Path path) {
            return new FileOutcome(path, Status.PRESERVED, null);
        }

        public static FileOutcome failed(Path path, String detail) {
            return new FileOutcome(path, Status.FAILED, detail);
        }
    }
}
