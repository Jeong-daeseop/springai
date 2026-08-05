package com.krdevops.springai.service.write;

import java.util.List;

/**
 * WP7/ARCH-0703: {@link ApprovedProjectWritePort#apply}의 결과. {@code CONFLICT}는 아무 것도
 * 쓰지 않은 상태(drift 감지, {@link com.krdevops.springai.model.write.ProjectWritePolicy#ATOMIC_APPROVED}
 * 전용), {@code ROLLED_BACK}는 적용 도중 실패해 원래 상태로 복구를 시도한 상태다.
 */
public record ApplyOutcome(
        Status status,
        List<String> appliedPaths,
        List<String> conflictingPaths,
        String backupPath,
        String failureDetail
) {
    public ApplyOutcome {
        appliedPaths = appliedPaths == null ? List.of() : List.copyOf(appliedPaths);
        conflictingPaths = conflictingPaths == null ? List.of() : List.copyOf(conflictingPaths);
    }

    /**
     * @param backupPath 적용 전 원본 파일들을 복사해 둔 디렉터리. 성공 이후에도 삭제하지 않는다 —
     *                    {@code ThymeleafProjectWorkflowService}가 지금까지 그래왔듯, 사후 수동
     *                    복구·검증을 위해 남겨두는 게 이 Port를 일반화하기 전의 원래 동작이다.
     */
    public static ApplyOutcome applied(List<String> appliedPaths, String backupPath) {
        return new ApplyOutcome(Status.APPLIED, appliedPaths, List.of(), backupPath, null);
    }

    public static ApplyOutcome conflict(List<String> conflictingPaths) {
        return new ApplyOutcome(Status.CONFLICT, List.of(), conflictingPaths, null, null);
    }

    /**
     * @param backupPath rollback이 사용한 백업 디렉터리. 파일은 이미 이 백업으로 복구됐지만,
     *                    무엇을 시도했었는지 사후 확인할 수 있도록 디렉터리 자체는 지우지 않는다.
     */
    public static ApplyOutcome rolledBack(String failureDetail, String backupPath) {
        return new ApplyOutcome(Status.ROLLED_BACK, List.of(), List.of(), backupPath, failureDetail);
    }

    public enum Status { APPLIED, CONFLICT, ROLLED_BACK }
}
