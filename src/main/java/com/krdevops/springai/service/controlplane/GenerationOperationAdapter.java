package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.GenerationOperation;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ValidationEvidence;

import java.util.List;
import java.util.Optional;

/** 기존 도메인 저장소를 수정하지 않고 공통 읽기 모델로 변환한다. */
public interface GenerationOperationAdapter {
    GenerationSourceType sourceType();
    Optional<GenerationOperation> find(String operationId);
    List<ValidationEvidence> evidence(String operationId);
}
