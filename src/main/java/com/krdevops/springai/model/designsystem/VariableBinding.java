package com.krdevops.springai.model.designsystem;

/** DesignSystemProfile.variables의 값. 논리 토큰(맵 키)이 실제 Figma Variable에 묶였는지 표현한다. */
public record VariableBinding(
        String variableId,
        String collectionName,
        ComponentBinding.BindingStatus status
) {
    public VariableBinding {
        status = status == null ? ComponentBinding.BindingStatus.UNBOUND : status;
    }
}
