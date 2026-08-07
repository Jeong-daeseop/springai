package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserGateDirectoryResolverTest {

    @TempDir Path root;
    @TempDir Path projectA;
    @TempDir Path projectB;

    private BrowserGateDirectoryResolver resolver() {
        return new BrowserGateDirectoryResolver(new SafePathResolver(), root);
    }

    @Test
    void sameProjectRootAlwaysResolvesToSameBaselineDirectory() {
        BrowserGateDirectoryResolver resolver = resolver();

        assertThat(resolver.baselineDirectory(projectA))
                .isEqualTo(resolver().baselineDirectory(projectA));
        assertThat(resolver.baselineDirectory(projectA).toString())
                .startsWith(root.toAbsolutePath().normalize().toString());
    }

    @Test
    void differentProjectRootsAreIsolated() {
        BrowserGateDirectoryResolver resolver = resolver();

        assertThat(resolver.baselineDirectory(projectA))
                .isNotEqualTo(resolver.baselineDirectory(projectB));
        assertThat(resolver.artifactDirectory(projectA, "op-1"))
                .isNotEqualTo(resolver.artifactDirectory(projectB, "op-1"));
    }

    @Test
    void baselineIsSharedAcrossOperationsButArtifactsAreNot() {
        BrowserGateDirectoryResolver resolver = resolver();

        assertThat(resolver.artifactDirectory(projectA, "op-1"))
                .isNotEqualTo(resolver.artifactDirectory(projectA, "op-2"));
        assertThat(resolver.baselineDirectory(projectA).getParent())
                .isEqualTo(resolver.artifactDirectory(projectA, "op-1").getParent().getParent());
    }

    @Test
    void projectKeyDoesNotLeakProjectPath() {
        assertThat(resolver().projectKey(projectA))
                .doesNotContain(projectA.getFileName().toString())
                .matches("[0-9a-f]{64}");
    }

    @Test
    void symlinkedProjectRootResolvesToSameKeyAsRealPath() throws Exception {
        Path link = projectB.resolve("linked-project");
        Files.createSymbolicLink(link, projectA);

        assertThat(resolver().projectKey(link)).isEqualTo(resolver().projectKey(projectA));
    }

    @Test
    void operationIdWithPathTraversalIsRejected() {
        assertThatThrownBy(() -> resolver().artifactDirectory(projectA, "../escape"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("BROWSER_GATE_OPERATION_ID_INVALID");
    }
}
