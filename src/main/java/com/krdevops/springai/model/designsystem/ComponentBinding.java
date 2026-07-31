package com.krdevops.springai.model.designsystem;

/** DesignSystemProfile.components의 값. 논리 컴포넌트 타입(맵 키)이 실제 Component Set에 묶였는지 표현한다. */
public record ComponentBinding(
        String componentSetKey,
        BindingStatus status
) {
    public ComponentBinding {
        status = status == null ? BindingStatus.UNBOUND : status;
    }

    public enum BindingStatus { BOUND, UNBOUND, DEPRECATED }
}
