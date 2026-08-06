package com.krdevops.springai.service.write;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** DB 없이 registry 동작을 검증하려는 단위 테스트·임베디드 사용을 위한 in-memory 구현. */
public class InMemoryProjectRootRegistryPort implements ProjectRootRegistryPort {

    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isRegistered(String canonicalRoot) {
        return registered.contains(canonicalRoot);
    }

    @Override
    public void register(String canonicalRoot, String registeredBy, String registrationSource) {
        registered.add(canonicalRoot);
    }
}
