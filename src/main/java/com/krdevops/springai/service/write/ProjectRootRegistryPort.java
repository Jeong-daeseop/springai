package com.krdevops.springai.service.write;

/**
 * WP7 6차 pass/ARCH-0704: 승인된 프로젝트 root의 영속 registry. {@link SafePathResolver}는 주어진
 * root 안에서의 이탈만 막지, 그 root 자체가 허용된 프로젝트 위치인지는 모른다 — 지금까지는
 * 각 호출자가 {@code CodeService.validateOutputRoot}를 먼저 부르는 규약에만 의존했는데, 새
 * 호출자가 이를 빠뜨리면 경계가 통째로 사라지는 구조적 결함이 있었다. 이 Port는
 * {@link ApprovedProjectWritePort} 자신이 항상 검증하도록 해서 그 결함을 닫는다.
 */
public interface ProjectRootRegistryPort {

    boolean isRegistered(String canonicalRoot);

    void register(String canonicalRoot, String registeredBy, String registrationSource);
}
