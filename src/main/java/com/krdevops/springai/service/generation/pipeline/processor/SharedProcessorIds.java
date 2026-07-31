package com.krdevops.springai.service.generation.pipeline.processor;

/**
 * 기능(CRUD/게시판/마스터-디테일) 공용 Processor의 id — 각 기능 Planner가 {@code ProcessorStep}을
 * 선언할 때 참조한다.
 */
public final class SharedProcessorIds {

    public static final String THYMELEAF_RUNTIME = "thymeleafRuntimeProcessor";
    public static final String CONTROLLER_SCAN = "controllerScanProcessor";
    public static final String MYBATIS_RUNTIME = "myBatisRuntimeProcessor";

    private SharedProcessorIds() {
    }
}
