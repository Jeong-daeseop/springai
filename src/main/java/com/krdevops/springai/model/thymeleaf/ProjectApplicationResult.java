package com.krdevops.springai.model.thymeleaf;

import java.time.Instant;
import java.util.List;

/**
 * I-7: 생성된 Thymeleaf 화면을 실제 프로젝트에 적용한 결과.
 * 배포된 파일 목록 및 검증 상태.
 */
public record ProjectApplicationResult(
        String applicationId,
        String projectPath,
        int filesDeployed,
        int deploymentFailures,
        List<DeployedScreenInfo> deployedScreens,
        DeploymentStatus status,
        String statusMessage,
        Instant appliedAt
) {
    public enum DeploymentStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED
    }

    public record DeployedScreenInfo(
            String screenId,
            String jspOriginalPath,
            String thymeleafDeployPath,
            String controllerRoute,
            boolean deployed,
            String deployError
    ) {
    }

    public boolean isSuccessful() {
        return status == DeploymentStatus.SUCCESS && deploymentFailures == 0;
    }
}
