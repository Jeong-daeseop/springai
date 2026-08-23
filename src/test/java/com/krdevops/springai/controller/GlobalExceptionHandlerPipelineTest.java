package com.krdevops.springai.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerPipelineTest {
    @Test void responseStatus_usesPipelineErrorContract() {
        var response = new GlobalExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Bundle 없음"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("HTTP_404");
    }
}
