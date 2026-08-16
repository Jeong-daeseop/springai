package com.krdevops.springai.config;

import com.krdevops.springai.service.designsystem.KrdsQnaFixtureBootstrapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 명시적으로 활성화된 환경에서만 KRDS 계약 Snapshot을 Runtime Repository에 적재한다. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.figma.contract-bootstrap", name = "enabled", havingValue = "true")
public class KrdsRuntimeContractBootstrap implements ApplicationRunner {

    private final KrdsQnaFixtureBootstrapService importService;

    public KrdsRuntimeContractBootstrap(KrdsQnaFixtureBootstrapService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            KrdsQnaFixtureBootstrapService.BootstrapResult result = importService.bootstrap();
            log.info("KRDS Q&A Fixture 적재 완료: profile={}/{}, registry={}, ruleSet={}/{}, patterns={}, inventory={}, screens={}",
                    result.profileId(), result.profileVersion(), result.registryVersion(), result.ruleSetId(),
                    result.ruleSetVersion(), result.patternCount(), result.inventoryVersion(), result.screenIds());
        } catch (RuntimeException exception) {
            log.error("KRDS Q&A Fixture 적재 실패: {}", exception.getMessage());
            throw exception;
        }
    }
}
