package com.krdevops.springai.service;

public class FigmaApiException extends RuntimeException {

    private final String code;
    private final int statusCode;

    public FigmaApiException(String code, int statusCode, String message) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public FigmaApiException(String code, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = statusCode;
    }

    public String code() { return code; }
    public int statusCode() { return statusCode; }
}
