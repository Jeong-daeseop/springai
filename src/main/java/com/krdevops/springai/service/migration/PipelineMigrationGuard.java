package com.krdevops.springai.service.migration;
import org.springframework.stereotype.Service;
@Service public class PipelineMigrationGuard { public void requireLegacyApply(boolean observationMode, boolean newApply){if(observationMode&&newApply)throw new IllegalStateException("관찰 모드에서는 신규 Apply 경로를 사용할 수 없습니다.");} public void requireScopeOwnershipRevision(boolean scope,boolean ownership,boolean revision){if(!scope||!ownership||!revision)throw new IllegalStateException("Scope·Ownership·Revision 검증이 필요합니다.");} }
