package com.krdevops.springai.service.write;

import com.krdevops.springai.model.write.ProjectChangeSet;

/**
 * WP7/ARCH-0703: 승인된 {@link ProjectChangeSet}을 실제 파일시스템에 적용하는 공용 계약. 일반
 * Generation Pipeline과 Thymeleaf Apply가 이 인터페이스 뒤에서 같은 staging→backup→atomic
 * replace→실패 시 rollback 의미를 공유한다. 승인 여부·hash 검증·Operation 상태 관리는 이 Port의
 * 책임이 아니다 — 그건 호출자(예: {@code ThymeleafProjectWorkflowService})가 이미 승인된
 * {@link ProjectChangeSet}만 넘긴다는 전제로 동작한다.
 */
public interface ApprovedProjectWritePort {

    ApplyOutcome apply(ProjectChangeSet changeSet);
}
