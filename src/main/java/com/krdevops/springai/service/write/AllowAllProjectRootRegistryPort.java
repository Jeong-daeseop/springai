package com.krdevops.springai.service.write;

/**
 * {@link FileSystemApprovedProjectWritePort}의 하위호환 생성자가 쓰는 기본값 — registry 검증을
 * 전부 통과 처리한다. DB 없이 Port 로직만 검증하려는 기존 단위 테스트·임베디드 사용을 깨지 않기
 * 위한 용도이며, 운영 환경에서는 절대 쓰지 않는다.
 */
public final class AllowAllProjectRootRegistryPort implements ProjectRootRegistryPort {

    @Override
    public boolean isRegistered(String canonicalRoot) {
        return true;
    }

    @Override
    public void register(String canonicalRoot, String registeredBy, String registrationSource) {
    }
}
