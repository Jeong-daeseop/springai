package com.krdevops.springai.service.contract;
import org.springframework.stereotype.Service;
@Service public class ImmutableApprovedArtifactGuard { public void requireWritable(boolean approved){if(approved)throw new IllegalStateException("APPROVED Artifact는 제자리 수정할 수 없습니다. 새 Version을 생성해야 합니다.");} public void requireApproved(boolean approved){if(!approved)throw new IllegalStateException("승인된 Artifact가 아닙니다.");} }
