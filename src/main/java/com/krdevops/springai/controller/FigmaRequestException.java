package com.krdevops.springai.controller;

public class FigmaRequestException extends RuntimeException {

    private final String code;

    public FigmaRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
