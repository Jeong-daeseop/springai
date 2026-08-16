package com.krdevops.springai.controller;

import com.krdevops.springai.service.figma.FigmaRestTokenService;
import com.krdevops.springai.service.figma.FigmaScreenExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FigmaExportControllerTest {

    private FigmaScreenExportService exportService;
    private FigmaRestTokenService restTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        exportService = mock(FigmaScreenExportService.class);
        restTokenService = new FigmaRestTokenService("token-secret", 900);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FigmaExportController(exportService, restTokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void issuesShortLivedTokenWhenEnabled() throws Exception {
        String token = mockMvc.perform(post("/api/figma/tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String tokenValue = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(token).get("token").asText();
        assertThat(restTokenService.verifyWithScopes(tokenValue).scopes())
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        FigmaRestTokenService.SCOPE_SCREENS_READ,
                        FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE,
                        FigmaRestTokenService.SCOPE_REPORTS_WRITE));
    }

    @Test
    void tokenIssuanceIsDisabledWithoutConfiguredSecret() throws Exception {
        MockMvc disabledMvc = MockMvcBuilders
                .standaloneSetup(new FigmaExportController(exportService, new FigmaRestTokenService("", 900)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        disabledMvc.perform(post("/api/figma/tokens"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIGMA_REST_TOKEN_DISABLED"));
    }

    @Test
    void downloadsUtf8FigmaExportBundleWithAttachmentFilename() throws Exception {
        when(exportService.findBundleVersionAsJson("user-list", 3))
                .thenReturn("{\"figmaScreenSpec\":{\"screenId\":\"user-list\"}}");

        mockMvc.perform(get("/api/figma/screens/user-list/download").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(content().contentType("application/json"))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("user-list-v3.figma-export-bundle.json")))
                .andExpect(content().json("{\"figmaScreenSpec\":{\"screenId\":\"user-list\"}}"));
    }

    @Test
    void matchingEtagReturnsNotModifiedWithoutBody() throws Exception {
        String json = "{\"figmaScreenSpec\":{\"screenId\":\"user-list\"}}";
        when(exportService.findBundleVersionAsJson("user-list", 3)).thenReturn(json);

        String etag = mockMvc.perform(
                        get("/api/figma/screens/user-list/download").param("version", "3"))
                .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/figma/screens/user-list/download")
                        .param("version", "3")
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(content().string(""));
    }

    @Test
    void missingScreenReturnsCodedNotFoundResponse() throws Exception {
        when(exportService.findLatest("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/figma/screens/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FIGMA_SCREEN_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/figma/screens/missing"));
    }

    @Test
    void invalidExportReturnsCodedBadRequestResponse() throws Exception {
        when(exportService.export(any())).thenThrow(new IllegalArgumentException("APPROVED 상태가 아닙니다."));

        mockMvc.perform(post("/api/figma/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "screenSpecificationId":"users",
                                  "pageId":"user-list",
                                  "designSystemProfileId":"ftc-krds",
                                  "viewport":"DESKTOP"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIGMA_EXPORT_INVALID"));
    }
}
