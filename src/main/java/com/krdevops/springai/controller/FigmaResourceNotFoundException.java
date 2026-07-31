package com.krdevops.springai.controller;

public class FigmaResourceNotFoundException extends RuntimeException {

    private final String code;

    public FigmaResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
