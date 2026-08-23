package com.krdevops.springai.service.security;
import org.springframework.stereotype.Service;
@Service public class ArtifactSecurityPolicy {
 public void requireSafeFixture(String content){if(content==null||content.matches("(?is).*(password|api[_-]?key|authorization|cookie)\\s*[:=].*"))throw new IllegalStateException("비밀정보·운영 인증 정보가 Artifact에 포함되어 있습니다.");}
 public void requireNoExecutionPermission(String serialized){if(serialized!=null&&serialized.contains("EXECUTE_SERVER"))throw new IllegalStateException("Bundle에 서버 실행 권한을 포함할 수 없습니다.");}
}
