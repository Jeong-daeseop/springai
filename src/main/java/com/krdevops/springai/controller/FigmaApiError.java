package com.krdevops.springai.controller;

import java.time.OffsetDateTime;

/** R6 Figma/Design System API의 표준 오류 응답. */
public record FigmaApiError(
        String code,
        String message,
        String path,
        OffsetDateTime timestamp
) {}
