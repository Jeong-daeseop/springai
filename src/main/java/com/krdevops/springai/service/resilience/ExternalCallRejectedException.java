package com.krdevops.springai.service.resilience;

public class ExternalCallRejectedException extends IllegalStateException {
    public ExternalCallRejectedException(ExternalDependency dependency, String reason) {
        super(dependency + " 외부 호출이 격리 정책에 의해 거부되었습니다: " + reason);
    }
}

