package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.RegenerationDiffResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegenerationDiffServiceTest {

    private final RegenerationDiffService service = new RegenerationDiffService();

    @Test
    void noPreviousContractMeansNoReviewNeeded() {
        RegenerationDiffResult diff = service.diff(null, contract("GET", List.of()));

        assertThat(diff.hasPrevious()).isFalse();
        assertThat(diff.requiresReview()).isFalse();
    }

    @Test
    void identicalSecurityEvidenceAndHttpMethodDoNotRequireReview() {
        ThymeleafBindingContract previous = contract("GET", List.of("@PreAuthorize(\"hasRole('ADMIN')\")"));
        ThymeleafBindingContract current = contract("GET", List.of("@PreAuthorize(\"hasRole('ADMIN')\")"));

        RegenerationDiffResult diff = service.diff(previous, current);

        assertThat(diff.hasPrevious()).isTrue();
        assertThat(diff.requiresReview()).isFalse();
    }

    @Test
    void addedSecurityEvidenceRequiresReview() {
        ThymeleafBindingContract previous = contract("GET", List.of());
        ThymeleafBindingContract current = contract("GET", List.of("@PreAuthorize(\"hasRole('ADMIN')\")"));

        RegenerationDiffResult diff = service.diff(previous, current);

        assertThat(diff.permissionChanged()).isTrue();
        assertThat(diff.addedSecurityEvidence()).containsExactly("@PreAuthorize(\"hasRole('ADMIN')\")");
        assertThat(diff.removedSecurityEvidence()).isEmpty();
        assertThat(diff.requiresReview()).isTrue();
    }

    @Test
    void removedSecurityEvidenceRequiresReview() {
        ThymeleafBindingContract previous = contract("GET", List.of("@Secured(\"ROLE_ADMIN\")"));
        ThymeleafBindingContract current = contract("GET", List.of());

        RegenerationDiffResult diff = service.diff(previous, current);

        assertThat(diff.permissionChanged()).isTrue();
        assertThat(diff.removedSecurityEvidence()).containsExactly("@Secured(\"ROLE_ADMIN\")");
        assertThat(diff.addedSecurityEvidence()).isEmpty();
        assertThat(diff.requiresReview()).isTrue();
    }

    @Test
    void httpMethodChangeRequiresReview() {
        ThymeleafBindingContract previous = contract("GET", List.of());
        ThymeleafBindingContract current = contract("POST", List.of());

        RegenerationDiffResult diff = service.diff(previous, current);

        assertThat(diff.httpMethodChanged()).isTrue();
        assertThat(diff.previousHttpMethod()).isEqualTo("GET");
        assertThat(diff.currentHttpMethod()).isEqualTo("POST");
        assertThat(diff.requiresReview()).isTrue();
    }

    private ThymeleafBindingContract contract(String httpMethod, List<String> securityEvidence) {
        ThymeleafRouteBinding route = new ThymeleafRouteBinding(
                "/employer/list.do", httpMethod, "selectEmployerList", null, null,
                false, false, securityEvidence);
        return new ThymeleafBindingContract(
                "employer-list", LegacyScreenRole.LIST, route, List.of(),
                List.of("empNm"), "result", List.of(), null,
                List.of(), List.of(), BindingContractStatus.RESOLVED, List.of(), null, Instant.now());
    }
}
