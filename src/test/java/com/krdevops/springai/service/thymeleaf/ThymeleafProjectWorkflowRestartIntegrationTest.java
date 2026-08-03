package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ThymeleafProjectOperationRepository;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-0418: RISK-03("승인 직후 서버가 재시작되어 Apply 또는 rollback 근거가 사라진다")가
 * 실제로 닫혔는지, 서비스 공개 API 수준에서 end-to-end로 증명한다.
 *
 * <p>{@code ThymeleafProjectWorkflowService} 인스턴스를 두 번 새로 만들어 "재시작"을
 * 시뮬레이션한다 — 상태가 인스턴스의 {@code ConcurrentHashMap}이 아니라 실제 MySQL에
 * 있어야만, 두 번째 인스턴스가 첫 번째 인스턴스의 Preview·Approve 결과를 이어서 Apply할 수 있다.
 */
class ThymeleafProjectWorkflowRestartIntegrationTest {

    @TempDir Path projectRoot;

    private ThymeleafProjectWorkflowService newServiceInstance() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
                System.getenv().getOrDefault("DB_USERNAME", "ebt"),
                System.getenv().getOrDefault("DB_PASSWORD", "ebt01")));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ThymeleafOperationStore realStore = new ThymeleafProjectOperationRepository(jdbcTemplate, objectMapper);
        return new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(),
                new OperationHashFactory(objectMapper), null, realStore);
    }

    @Test
    void previewAndApproveSurviveServiceRestart_thenApplySucceedsOnNewInstance() throws Exception {
        // previewHash는 (files, designRevision)로만 결정되는 멱등성 key라, 매 실행 같은 내용을
        // 쓰면 이전 테스트 실행이 실 DB에 남긴 Operation을 그대로 재사용해버린다(의도된 동작,
        // ARCH-0411). 이 테스트가 매번 새 Operation으로 시작하도록 내용을 실행마다 다르게 만든다.
        String relative = "src/main/resources/templates/users/list.html";
        String html = "<div><form th:action=\"/users\"></form><!-- " + UUID.randomUUID() + " --></div>";

        // "인스턴스 1" — 서버가 재시작 전 살아있던 프로세스를 흉내낸다.
        ThymeleafProjectWorkflowService beforeRestart = newServiceInstance();
        var preview = beforeRestart.preview(projectRoot, Map.of(relative, html));
        var approved = beforeRestart.approve(preview.operation().operationId(), preview.previewHash());
        assertThat(approved.operation().status()).isEqualTo(ProjectOperationStatus.APPROVED);

        // "재시작" — 완전히 새 인스턴스. ConcurrentHashMap이었다면 여기서 상태가 사라졌을 것이다.
        ThymeleafProjectWorkflowService afterRestart = newServiceInstance();
        var recovered = afterRestart.find(preview.operation().operationId()).orElseThrow();
        assertThat(recovered.operation().status()).isEqualTo(ProjectOperationStatus.APPROVED);

        var applied = afterRestart.apply(preview.operation().operationId());
        assertThat(applied.operation().status()).isEqualTo(ProjectOperationStatus.APPLIED);
        assertThat(projectRoot.resolve(relative)).hasContent(html);
    }
}
