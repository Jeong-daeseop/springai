package com.krdevops.springai.model;

public record MenuRegistrationSpec(
        int upperMenuNo,
        String urlPrefix,
        String menuNm,
        String progrmFileNm
) {}
