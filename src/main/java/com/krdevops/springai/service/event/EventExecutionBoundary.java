package com.krdevops.springai.service.event;
import org.springframework.stereotype.Service;
@Service public class EventExecutionBoundary { public void requireNonMutating(boolean commit, boolean apply, boolean deploy){if(commit||apply||deploy)throw new IllegalStateException("Event는 Commit·Apply·배포를 직접 실행할 수 없습니다.");} }
