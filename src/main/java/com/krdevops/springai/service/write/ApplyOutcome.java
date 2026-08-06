package com.krdevops.springai.service.write;

import java.util.List;
import java.util.Map;

/**
 * WP7/ARCH-0703: {@link ApprovedProjectWritePort#apply}의 결과. {@code CONFLICT}는 아무 것도
 * 쓰지 않은 상태(drift 감지, {@link com.krdevops.springai.model.write.ProjectWritePolicy#ATOMIC_APPROVED}
 * 전용), {@code ROLLED_BACK}는 적용 도중 실패해 원래 상태로 복구를 시도한 상태다. {@code
 * PARTIALLY_APPLIED}는 {@link com.krdevops.springai.model.write.ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY}
 * 전용 — 일부 파일은 저장됐고 일부는 실패했는데, 성공한 파일을 롤백하지 않는다.
 */
public record ApplyOutcome(
        Status status,
        List<String> appliedPaths,
        List<String> conflictingPaths,
        Map<String, String> failureMessages,
        String backupPath,
        String failureDetail
) {
    public ApplyOutcome {
        appliedPaths = appliedPaths == null ? List.of() : List.copyOf(appliedPaths);
        conflictingPaths = conflictingPaths == null ? List.of() : List.copyOf(conflictingPaths);
        failureMessages = failureMessages == null ? Map.of() : Map.copyOf(failureMessages);
    }

    /**
     * @param backupPath 적용 전 원본 파일들을 복사해 둔 디렉터리. 성공 이후에도 삭제하지 않는다 —
     *                    {@code ThymeleafProjectWorkflowService}가 지금까지 그래왔듯, 사후 수동
     *                    복구·검증을 위해 남겨두는 게 이 Port를 일반화하기 전의 원래 동작이다.
     */
    public static ApplyOutcome applied(List<String> appliedPaths, String backupPath) {
        return new ApplyOutcome(Status.APPLIED, appliedPaths, List.of(), Map.of(), backupPath, null);
    }

    public static ApplyOutcome conflict(List<String> conflictingPaths) {
        return new ApplyOutcome(Status.CONFLICT, List.of(), conflictingPaths, Map.of(), null, null);
    }

    /**
     * @param backupPath rollback이 사용한 백업 디렉터리. 파일은 이미 이 백업으로 복구됐지만,
     *                    무엇을 시도했었는지 사후 확인할 수 있도록 디렉터리 자체는 지우지 않는다.
     */
    public static ApplyOutcome rolledBack(String failureDetail, String backupPath) {
        return new ApplyOutcome(Status.ROLLED_BACK, List.of(), List.of(), Map.of(), backupPath, failureDetail);
    }

    /** @param failureMessages 실패한 경로 → 실패 사유. 성공한 경로({@code appliedPaths})는 롤백되지 않는다. */
    public static ApplyOutcome partiallyApplied(List<String> appliedPaths, Map<String, String> failureMessages) {
        return new ApplyOutcome(Status.PARTIALLY_APPLIED, appliedPaths, List.of(), failureMessages, null, null);
    }

    /**
     * ARCH-0713: 적용 실패 후 복구(rollback) 자체도 일부 실패한 경우 — {@code rolledBack}과 달리
     * "원래 상태로 되돌렸다"를 보장하지 않는다. {@code failureMessages}는 복구에 실패한 경로 →
     * 사유(예: backup에서 복사 실패)다. backup 디렉터리는 지우지 않으므로 사후 수동 복구가 필요하다.
     */
    public static ApplyOutcome rollbackFailed(
            String failureDetail, String backupPath, Map<String, String> rollbackFailureMessages) {
        return new ApplyOutcome(Status.ROLLBACK_FAILED, List.of(), List.of(), rollbackFailureMessages, backupPath,
                failureDetail);
    }

    public enum Status { APPLIED, CONFLICT, ROLLED_BACK, ROLLBACK_FAILED, PARTIALLY_APPLIED }
}
