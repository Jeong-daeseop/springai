package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import com.krdevops.springai.model.write.ProjectChangeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaAssetDownloadServiceTest {

    private CodeService codeService(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        return new CodeService(properties);
    }

    @Test
    void rejectsMalformedNodeIdBeforeCallingFigmaApi(@TempDir Path root) {
        FigmaApiClient apiClient = mock(FigmaApiClient.class);
        FigmaAssetDownloadService service = new FigmaAssetDownloadService(
                apiClient, mock(ApprovedProjectWritePort.class), codeService(root));

        assertThatThrownBy(() -> service.downloadAssets("file-key", List.of("not-a-node-id"), root.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-node-id");
        verify(apiClient, never()).queryImages(any(), any());
    }

    @Test
    void rejectsDisallowedImageHost(@TempDir Path root) {
        FigmaApiClient apiClient = mock(FigmaApiClient.class);
        ApprovedProjectWritePort writePort = mock(ApprovedProjectWritePort.class);
        when(apiClient.queryImages("file-key", List.of("1:2"))).thenReturn(
                new FigmaApiClient.FigmaImageUrls(
                        Map.of("1:2", "https://evil.example.com/steal.png"), List.of(), Instant.now()));
        FigmaAssetDownloadService service = new FigmaAssetDownloadService(
                apiClient, writePort, codeService(root));

        assertThatThrownBy(() -> service.downloadAssets("file-key", List.of("1:2"), root.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("evil.example.com");
        verify(writePort, never()).apply(any());
    }

    @Test
    void skipsNodesThatFailedToRenderAndReturnsEmptyWhenAllFail(@TempDir Path root) {
        FigmaApiClient apiClient = mock(FigmaApiClient.class);
        ApprovedProjectWritePort writePort = mock(ApprovedProjectWritePort.class);
        when(apiClient.queryImages("file-key", List.of("1:2"))).thenReturn(
                new FigmaApiClient.FigmaImageUrls(Map.of(), List.of("1:2"), Instant.now()));
        FigmaAssetDownloadService service = new FigmaAssetDownloadService(
                apiClient, writePort, codeService(root));

        List<String> saved = service.downloadAssets("file-key", List.of("1:2"), root.toString());

        assertThat(saved).isEmpty();
        verify(writePort, never()).apply(any());
    }

    @Test
    void savesSuccessfullyDownloadedAssetsUnderFigmaImagesDirectory(@TempDir Path root) throws Exception {
        FigmaApiClient apiClient = mock(FigmaApiClient.class);
        when(apiClient.queryImages("file-key", List.of("1:2"))).thenReturn(
                new FigmaApiClient.FigmaImageUrls(
                        Map.of("1:2", "https://s3-alpha-sig.figma.com/img/abc/1-2.png"),
                        List.of(), Instant.now()));
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        FigmaAssetDownloadService service = new FigmaAssetDownloadService(apiClient, writePort, codeService(root)) {
            @Override
            byte[] download(String url) {
                return "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
            }
        };

        List<String> saved = service.downloadAssets("file-key", List.of("1:2"), root.toString());

        assertThat(saved).containsExactly("src/main/resources/static/images/figma/1-2.png");
        Path savedFile = root.resolve(saved.get(0));
        assertThat(Files.readString(savedFile, StandardCharsets.UTF_8)).isEqualTo("fake-png-bytes");
    }

    @Test
    void bubblesUpWriteConflictAsIllegalStateException(@TempDir Path root) {
        FigmaApiClient apiClient = mock(FigmaApiClient.class);
        when(apiClient.queryImages("file-key", List.of("1:2"))).thenReturn(
                new FigmaApiClient.FigmaImageUrls(
                        Map.of("1:2", "https://s3-alpha-sig.figma.com/img/abc/1-2.png"),
                        List.of(), Instant.now()));
        ApprovedProjectWritePort writePort = mock(ApprovedProjectWritePort.class);
        when(writePort.apply(any())).thenReturn(new ApplyOutcome(
                ApplyOutcome.Status.CONFLICT, List.of(), List.of("some/path"), Map.of(), null, null));
        FigmaAssetDownloadService service = new FigmaAssetDownloadService(apiClient, writePort, codeService(root)) {
            @Override
            byte[] download(String url) {
                return "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
            }
        };

        assertThatThrownBy(() -> service.downloadAssets("file-key", List.of("1:2"), root.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFLICT");
    }
}
